package app.wearly.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.KeySource;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.ai.domain.SealedSecret;
import app.wearly.ai.domain.SecretUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 키 고르기·복호화 캐시·무효화 (가짜 포트, Spring 없이) */
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyServiceTest {

  static final AiProviderSetting OPENAI =
      new AiProviderSetting(
          "openai", AiProviderType.OPENAI, "o", "m", "OPENAI_API_KEY", true, 1, Map.of(), null);

  final Map<String, ProviderSecret> rows = new HashMap<>();
  final AtomicInteger loads = new AtomicInteger();
  boolean enabled = true;
  boolean broken = false;
  final Map<String, String> env = new HashMap<>();

  final ProviderSecretPort secrets =
      new ProviderSecretPort() {
        @Override
        public Optional<ProviderSecret> find(String providerId) {
          loads.incrementAndGet();
          return Optional.ofNullable(rows.get(providerId));
        }

        @Override
        public ProviderSecret save(ProviderSecret s) {
          rows.put(s.providerId(), s);
          return s;
        }

        @Override
        public boolean delete(String providerId) {
          return rows.remove(providerId) != null;
        }
      };

  /** 가짜 암호: 평문을 뒤집어 둔다. broken이면 복호화 실패 */
  final SecretCipherPort cipher =
      new SecretCipherPort() {
        @Override
        public boolean enabled() {
          return enabled;
        }

        @Override
        public SealedSecret seal(String plaintext, String aad) {
          return new SealedSecret(
              new StringBuilder(plaintext).reverse().toString().getBytes(StandardCharsets.UTF_8),
              new byte[12],
              1);
        }

        @Override
        public String open(SealedSecret sealed, String aad) {
          if (broken) {
            throw new SecretUnavailableException("복호화에 실패했어요.");
          }
          return new StringBuilder(new String(sealed.ciphertext(), StandardCharsets.UTF_8))
              .reverse()
              .toString();
        }
      };

  final ApiKeyService service =
      new ApiKeyService(name -> Optional.ofNullable(env.get(name)), secrets, cipher, Runnable::run);

  void storeDbKey(String key) {
    rows.put(
        "openai",
        new ProviderSecret(
            "openai",
            cipher.seal(key, "openai"),
            key.substring(key.length() - 4),
            7L,
            Instant.parse("2026-10-17T00:00:00Z")));
    service.invalidate("openai");
  }

  @Test
  void DB_키_다음_환경변수_다음_없음() {
    assertThat(service.state(OPENAI).source()).isEqualTo(KeySource.NONE);
    env.put("OPENAI_API_KEY", "env-key-value");
    assertThat(service.find(OPENAI)).contains("env-key-value");
    assertThat(service.state(OPENAI).source()).isEqualTo(KeySource.ENV);
    assertThat(service.state(OPENAI).last4()).isNull();

    storeDbKey("db-key-value-1234");
    ApiKeyService.KeyState state = service.state(OPENAI);
    assertThat(state.source()).isEqualTo(KeySource.DB);
    assertThat(state.choice().value()).isEqualTo("db-key-value-1234");
    assertThat(state.last4()).isEqualTo("1234");
    assertThat(state.updatedBy()).isEqualTo(7L);
    assertThat(state.toString()).doesNotContain("db-key-value");

    rows.remove("openai");
    service.invalidate("openai");
    assertThat(service.find(OPENAI)).contains("env-key-value");
  }

  @Test
  void 복호화한_키는_캐시하고_무효화하면_다시_읽는다() {
    storeDbKey("db-key-value-aaaa");
    service.state(OPENAI);
    service.state(OPENAI);
    service.configured(OPENAI);
    assertThat(loads).hasValue(1);
    storeDbKey("db-key-value-bbbb");
    assertThat(service.find(OPENAI)).contains("db-key-value-bbbb");
    assertThat(loads).hasValue(2);
  }

  @Test
  void 마스터_키가_없으면_환경변수_키를_그대로_쓴다() {
    env.put("OPENAI_API_KEY", "env-key-value");
    storeDbKey("db-key-value-1234");
    enabled = false;
    assertThat(service.state(OPENAI).source()).isEqualTo(KeySource.ENV);
    assertThat(service.storeEnabled()).isFalse();
  }

  @Test
  void 복호화에_실패하면_쓸_수_없고_ERROR_로그에_키가_없다(CapturedOutput output) {
    env.put("OPENAI_API_KEY", "env-key-value");
    broken = true;
    storeDbKey("db-key-value-1234");
    ApiKeyService.KeyState state = service.state(OPENAI);
    assertThat(state.source()).isEqualTo(KeySource.NONE);
    assertThat(state.dbUnreadable()).isTrue();
    assertThat(service.configured(OPENAI)).isFalse();
    assertThat(output.getAll())
        .contains("ERROR")
        .contains("openai")
        .doesNotContain("db-key-value")
        .doesNotContain("env-key-value");
  }
}
