package app.wearly.ai.application.service;

import app.wearly.ai.application.port.out.ApiKeyEnvPort;
import app.wearly.ai.application.port.out.ProviderSecretPort;
import app.wearly.ai.application.port.out.SecretCipherPort;
import app.wearly.ai.application.port.out.TransactionHooksPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.ApiKeyRules;
import app.wearly.ai.domain.ApiKeyRules.Choice;
import app.wearly.ai.domain.KeySource;
import app.wearly.ai.domain.ProviderSecret;
import app.wearly.ai.domain.SecretUnavailableException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공급자가 쓸 키 고르기 (07 문서 6장): 웹에서 넣은 키(DB, 복호화되면) → api_key_env 환경변수 → 없음. 복호화한 DB 키는 메모리에 캐시하고, 키를
 * 바꾸거나 지우면 비운다(트랜잭션이 끝난 뒤에도 한 번 더). 키 값은 로그·예외 메시지에 남기지 않는다.
 */
@Service
public class ApiKeyService {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

  private final ApiKeyEnvPort env;
  private final ProviderSecretPort secrets;
  private final SecretCipherPort cipher;
  private final TransactionHooksPort tx;

  /** provider_id → DB 키 (행이 없으면 빈 값) */
  private final Map<String, Optional<StoredKey>> cache = new ConcurrentHashMap<>();

  public ApiKeyService(
      ApiKeyEnvPort env,
      ProviderSecretPort secrets,
      SecretCipherPort cipher,
      TransactionHooksPort tx) {
    this.env = env;
    this.secrets = secrets;
    this.cipher = cipher;
    this.tx = tx;
  }

  /** DB에 있는 키 한 행 (복호화에 실패했으면 value가 비어 있다) */
  private record StoredKey(
      Optional<String> value, String last4, Instant updatedAt, Long updatedBy) {}

  /**
   * 지금 쓰는 키와 표시 정보. last4·updatedAt·updatedBy는 DB 키를 쓸 때만 있다. dbUnreadable = 웹 키가 있는데 복호화하지 못해 이
   * 공급자를 쓸 수 없음
   */
  public record KeyState(
      Choice choice, String last4, Instant updatedAt, Long updatedBy, boolean dbUnreadable) {

    public KeySource source() {
      return choice.source();
    }

    @Override
    public String toString() {
      return "KeyState[" + choice.source() + "]";
    }
  }

  public boolean storeEnabled() {
    return cipher.enabled();
  }

  public KeyState state(AiProviderSetting s) {
    boolean enabled = cipher.enabled();
    Optional<StoredKey> db = enabled ? stored(s.providerId()) : Optional.empty();
    Choice choice =
        ApiKeyRules.choose(
            enabled, db.isPresent(), db.flatMap(StoredKey::value), env.resolve(s.apiKeyEnv()));
    if (choice.source() == KeySource.DB) {
      StoredKey k = db.get();
      return new KeyState(choice, k.last4(), k.updatedAt(), k.updatedBy(), false);
    }
    return new KeyState(choice, null, null, null, db.isPresent() && db.get().value().isEmpty());
  }

  public Optional<String> find(AiProviderSetting s) {
    Choice c = state(s).choice();
    return c.present() ? Optional.of(c.value()) : Optional.empty();
  }

  public boolean configured(AiProviderSetting s) {
    return state(s).choice().present();
  }

  /** 키를 바꾸거나 지운 뒤 (공급자를 지울 때도) */
  public void invalidate(String providerId) {
    cache.remove(providerId);
    tx.afterCompletion(() -> cache.remove(providerId));
  }

  private Optional<StoredKey> stored(String providerId) {
    return cache.computeIfAbsent(providerId, this::load);
  }

  private Optional<StoredKey> load(String providerId) {
    Optional<ProviderSecret> found = secrets.find(providerId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    ProviderSecret s = found.get();
    Optional<String> value;
    try {
      value = Optional.of(cipher.open(s.sealed(), providerId));
    } catch (SecretUnavailableException e) {
      log.error(
          "AI 공급자 {}의 웹 입력 키를 복호화하지 못해 이 공급자를 쓸 수 없어요 (WEARLY_SECRET_KEY가 바뀌었을 수 있음): {}",
          providerId,
          e.getMessage());
      value = Optional.empty();
    }
    return Optional.of(new StoredKey(value, s.last4(), s.updatedAt(), s.updatedBy()));
  }
}
