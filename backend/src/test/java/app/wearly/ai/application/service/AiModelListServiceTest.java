package app.wearly.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.application.port.in.dto.AiModelListDto;
import app.wearly.ai.application.port.out.AiModelCatalogPort;
import app.wearly.ai.application.port.out.AiProviderPort;
import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.BuiltInModels;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.ai.domain.SealedSecret;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 모델 목록: 키 없음·실패 시 내장 목록, 10분 캐시, 키 지문·무효화·refresh (가짜 포트, Spring 없이) */
@ExtendWith(OutputCaptureExtension.class)
class AiModelListServiceTest {

  static final String KEY_A = "sk-model-list-key-aaaaaaaaaaaa";
  static final String KEY_B = "sk-model-list-key-bbbbbbbbbbbb";

  final Map<String, AiProviderSetting> rows = new HashMap<>();
  final Map<String, String> env = new HashMap<>();

  /** 가짜 제공사: 부를 때마다 기록하고, failWith가 있으면 그 사유로 실패 */
  final List<String> calls = new ArrayList<>();

  String failWith = null;
  List<AiModelEntry> liveModels =
      List.of(
          AiModelEntry.live(
              "claude-opus-5-5", "Claude Opus 5.5", Instant.parse("2026-08-01T00:00:00Z")),
          AiModelEntry.live("claude-new", "Claude New", Instant.parse("2026-10-01T00:00:00Z")),
          AiModelEntry.live(
              "claude-sonnet-5-5", "Claude Sonnet 5.5", Instant.parse("2026-09-01T00:00:00Z")));

  Instant now = Instant.parse("2026-10-17T00:00:00Z");

  final AiProviderPort settings =
      new AiProviderPort() {
        @Override
        public List<AiProviderSetting> findAllOrdered() {
          return List.copyOf(rows.values());
        }

        @Override
        public Optional<AiProviderSetting> findById(String providerId) {
          return Optional.ofNullable(rows.get(providerId));
        }

        @Override
        public boolean exists(String providerId) {
          return rows.containsKey(providerId);
        }

        @Override
        public int maxPriority() {
          return 0;
        }

        @Override
        public AiProviderSetting create(AiProviderSetting setting) {
          rows.put(setting.providerId(), setting);
          return setting;
        }

        @Override
        public AiProviderSetting update(AiProviderSetting setting) {
          return create(setting);
        }

        @Override
        public void delete(String providerId) {
          rows.remove(providerId);
        }
      };

  final ProviderSecretPort noSecrets =
      new ProviderSecretPort() {
        @Override
        public Optional<ProviderSecret> find(String providerId) {
          return Optional.empty();
        }

        @Override
        public ProviderSecret save(ProviderSecret s) {
          return s;
        }

        @Override
        public boolean delete(String providerId) {
          return false;
        }
      };

  /** 마스터 키 없음: 서버 설정(환경변수) 키만 쓴다 */
  final SecretCipherPort disabled =
      new SecretCipherPort() {
        @Override
        public boolean enabled() {
          return false;
        }

        @Override
        public SealedSecret seal(String plaintext, String aad) {
          throw new UnsupportedOperationException();
        }

        @Override
        public String open(SealedSecret sealed, String aad) {
          throw new UnsupportedOperationException();
        }
      };

  final AiModelCatalogPort catalog =
      (s, apiKey) -> {
        calls.add(s.providerId() + ":" + apiKey);
        if (failWith != null) {
          throw new AiProviderException(failWith, "Claude 호출 실패: echo " + apiKey);
        }
        return liveModels;
      };

  final BuiltInModels builtIn =
      new BuiltInModels(
          Map.of(
              AiProviderType.CLAUDE,
              List.of(
                  new AiModelEntry("claude-opus-5-5", "Claude Opus 5.5", null, false),
                  new AiModelEntry("claude-sonnet-5-5", "Claude Sonnet 5.5", null, true),
                  new AiModelEntry("claude-haiku-5-5", "Claude Haiku 5.5", null, false))));

  final AiModelListService service =
      new AiModelListService(
          settings,
          new ApiKeyService(
              name -> Optional.ofNullable(env.get(name)), noSecrets, disabled, Runnable::run),
          catalog,
          builtIn,
          new Clock() {
            @Override
            public ZoneOffset getZone() {
              return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
              return this;
            }

            @Override
            public Instant instant() {
              return now;
            }
          });

  void provider(String id, AiProviderType type, String env) {
    rows.put(id, new AiProviderSetting(id, type, id, "m", env, true, 1, Map.of(), null));
  }

  @Test
  void 키가_없으면_제공사를_부르지_않고_내장_목록과_NO_API_KEY() {
    provider("claude", AiProviderType.CLAUDE, "ANTHROPIC_API_KEY");
    AiModelListDto r = service.models("claude", false);

    assertThat(calls).isEmpty();
    assertThat(r.source()).isEqualTo(AiModelListDto.CATALOG);
    assertThat(r.fetchedAt()).isEqualTo(now);
    assertThat(r.error().code()).isEqualTo(AiModelListDto.NO_API_KEY);
    assertThat(r.error().message()).isNotBlank();
    assertThat(r.models())
        .extracting(AiModelListDto.Model::id)
        .containsExactly("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5");
    assertThat(r.models().get(0).recommended()).isTrue();
    assertThat(r.models()).allMatch(m -> m.createdAt() == null);

    // 내장 목록이 없는 종류는 빈 목록
    provider("openai", AiProviderType.OPENAI, "OPENAI_API_KEY");
    assertThat(service.models("openai", false).models()).isEmpty();
  }

  @Test
  void 없는_공급자는_404() {
    assertThatThrownBy(() -> service.models("nope", false))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void 제공사_목록에_추천을_붙이고_10분_캐시한다() {
    provider("claude", AiProviderType.CLAUDE, "ANTHROPIC_API_KEY");
    env.put("ANTHROPIC_API_KEY", KEY_A);

    AiModelListDto first = service.models("claude", false);
    assertThat(first.source()).isEqualTo(AiModelListDto.LIVE);
    assertThat(first.error()).isNull();
    assertThat(first.fetchedAt()).isEqualTo(now);
    assertThat(first.models())
        .extracting(AiModelListDto.Model::id)
        .containsExactly("claude-sonnet-5-5", "claude-new", "claude-opus-5-5");
    assertThat(first.models())
        .extracting(AiModelListDto.Model::recommended)
        .containsExactly(true, false, false);
    assertThat(calls).containsExactly("claude:" + KEY_A);

    // 10분 안에는 캐시 (fetchedAt은 처음 받은 시각)
    Instant fetched = now;
    now = now.plus(Duration.ofMinutes(9));
    AiModelListDto cached = service.models("claude", false);
    assertThat(calls).hasSize(1);
    assertThat(cached.fetchedAt()).isEqualTo(fetched);

    // refresh=true면 캐시를 무시한다
    service.models("claude", true);
    assertThat(calls).hasSize(2);

    // 10분이 지나면 다시 받는다
    now = now.plus(Duration.ofMinutes(10));
    service.models("claude", false);
    assertThat(calls).hasSize(3);
  }

  @Test
  void 키가_바뀌면_지문이_달라져_다시_받고_무효화하면_다시_받는다() {
    provider("claude", AiProviderType.CLAUDE, "ANTHROPIC_API_KEY");
    env.put("ANTHROPIC_API_KEY", KEY_A);
    service.models("claude", false);
    service.models("claude", false);
    assertThat(calls).hasSize(1);

    env.put("ANTHROPIC_API_KEY", KEY_B);
    service.models("claude", false);
    assertThat(calls).containsExactly("claude:" + KEY_A, "claude:" + KEY_B);

    service.invalidate("claude");
    service.models("claude", false);
    assertThat(calls).hasSize(3);

    // 종류가 바뀌어도 다시 받는다
    provider("claude", AiProviderType.OPENAI, "ANTHROPIC_API_KEY");
    service.models("claude", false);
    assertThat(calls).hasSize(4);

    // 키를 지우면 내장 목록 (캐시를 쓰지 않는다)
    env.remove("ANTHROPIC_API_KEY");
    assertThat(service.models("claude", false).source()).isEqualTo(AiModelListDto.CATALOG);
    assertThat(calls).hasSize(4);
  }

  @Test
  void 실패하면_내장_목록과_MODEL_LIST_FAILED를_주고_키와_원문을_남기지_않는다(CapturedOutput output) {
    provider("claude", AiProviderType.CLAUDE, "ANTHROPIC_API_KEY");
    env.put("ANTHROPIC_API_KEY", KEY_A);
    failWith = "API_401";

    AiModelListDto r = service.models("claude", false);
    assertThat(r.source()).isEqualTo(AiModelListDto.CATALOG);
    assertThat(r.error().code()).isEqualTo(AiModelListDto.MODEL_LIST_FAILED);
    assertThat(r.error().message())
        .contains("HTTP 401")
        .doesNotContain(KEY_A)
        .doesNotContain("echo");
    assertThat(r.models()).extracting(AiModelListDto.Model::id).contains("claude-sonnet-5-5");

    // 실패는 캐시하지 않는다
    failWith = null;
    assertThat(service.models("claude", false).source()).isEqualTo(AiModelListDto.LIVE);
    assertThat(calls).hasSize(2);

    // 받아 둔 목록이 있어도 refresh가 실패하면 버린다 → 다음 요청에서 다시 받는다
    failWith = "TIMEOUT";
    assertThat(service.models("claude", true).source()).isEqualTo(AiModelListDto.CATALOG);
    failWith = null;
    assertThat(service.models("claude", false).source()).isEqualTo(AiModelListDto.LIVE);
    assertThat(calls).hasSize(4);
    assertThat(output.getAll()).doesNotContain(KEY_A);
  }

  @Test
  void 실패_사유별_문구() {
    assertThat(AiModelListService.failureMessage("API_403")).contains("HTTP 403").contains("키");
    assertThat(AiModelListService.failureMessage("API_500")).contains("HTTP 500");
    assertThat(AiModelListService.failureMessage("TIMEOUT")).contains("늦어");
    assertThat(AiModelListService.failureMessage("API_CLIENT")).contains("연결");
    assertThat(AiModelListService.failureMessage("BAD_RESPONSE")).contains("읽지");
    assertThat(AiModelListService.failureMessage(null)).isNotBlank();
  }
}
