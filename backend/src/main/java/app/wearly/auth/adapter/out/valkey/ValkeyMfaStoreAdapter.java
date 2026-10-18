package app.wearly.auth.adapter.out.valkey;

import app.wearly.auth.application.port.out.MfaChallengeStorePort;
import app.wearly.auth.domain.ClientInfo;
import app.wearly.auth.domain.ClientKind;
import app.wearly.auth.domain.PendingMfa;
import app.wearly.auth.domain.SealedMfaSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** MfaChallengeStorePort 구현 (Valkey). 키 이름은 {@link ValkeyKeys} */
@Component
class ValkeyMfaStoreAdapter implements MfaChallengeStorePort {

  private final StringRedisTemplate redis;

  ValkeyMfaStoreAdapter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void savePending(String tokenHash, PendingMfa p, Duration ttl) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("userId", Long.toString(p.userId()));
    f.put("client", p.client().client().name());
    f.put("userAgent", nullToEmpty(p.client().userAgent()));
    f.put("ip", nullToEmpty(p.client().ip()));
    f.put("createdAt", Long.toString(p.createdAt().toEpochMilli()));
    String key = ValkeyKeys.mfaPending(tokenHash);
    redis.opsForHash().putAll(key, f);
    redis.expire(key, ttl);
  }

  @Override
  public Optional<PendingMfa> findPending(String tokenHash) {
    Map<String, String> f = hash(ValkeyKeys.mfaPending(tokenHash));
    if (f.get("userId") == null) {
      return Optional.empty();
    }
    ClientInfo client =
        new ClientInfo(
            ClientKind.parse(f.get("client")).orElse(ClientKind.ADMIN),
            emptyToNull(f.get("userAgent")),
            emptyToNull(f.get("ip")));
    return Optional.of(
        new PendingMfa(
            Long.parseLong(f.get("userId")),
            client,
            Instant.ofEpochMilli(Long.parseLong(f.getOrDefault("createdAt", "0")))));
  }

  @Override
  public void deletePending(String tokenHash) {
    redis.delete(ValkeyKeys.mfaPending(tokenHash));
  }

  @Override
  public void saveSetupSecret(long userId, SealedMfaSecret s, Duration ttl) {
    String key = ValkeyKeys.mfaSetup(userId);
    Base64.Encoder b64 = Base64.getEncoder();
    redis
        .opsForHash()
        .putAll(
            key,
            Map.of(
                "cipher", b64.encodeToString(s.ciphertext()),
                "nonce", b64.encodeToString(s.nonce()),
                "keyVersion", Integer.toString(s.keyVersion())));
    redis.expire(key, ttl);
  }

  @Override
  public Optional<SealedMfaSecret> findSetupSecret(long userId) {
    Map<String, String> f = hash(ValkeyKeys.mfaSetup(userId));
    if (f.get("cipher") == null || f.get("nonce") == null) {
      return Optional.empty();
    }
    Base64.Decoder b64 = Base64.getDecoder();
    return Optional.of(
        new SealedMfaSecret(
            b64.decode(f.get("cipher")),
            b64.decode(f.get("nonce")),
            Integer.parseInt(f.getOrDefault("keyVersion", "1"))));
  }

  @Override
  public void deleteSetupSecret(long userId) {
    redis.delete(ValkeyKeys.mfaSetup(userId));
  }

  @Override
  public long addFailure(long userId, Duration ttl) {
    String key = ValkeyKeys.mfaFailures(userId);
    Long n = redis.opsForValue().increment(key);
    redis.expire(key, ttl);
    return n == null ? 1 : n;
  }

  @Override
  public void clearFailures(long userId) {
    redis.delete(ValkeyKeys.mfaFailures(userId));
  }

  @Override
  public void lock(long userId, Duration ttl) {
    redis.opsForValue().set(ValkeyKeys.mfaLock(userId), "1", ttl);
  }

  @Override
  public Optional<Duration> lockRemaining(long userId) {
    Long ms = redis.getExpire(ValkeyKeys.mfaLock(userId), TimeUnit.MILLISECONDS);
    // -2: 키 없음, -1: 만료 없음(만들 때 항상 TTL을 주므로 없지만 잠긴 것으로 본다)
    if (ms == null || ms == -2) {
      return Optional.empty();
    }
    return Optional.of(ms < 0 ? Duration.ZERO : Duration.ofMillis(ms));
  }

  private Map<String, String> hash(String key) {
    Map<Object, Object> raw = redis.opsForHash().entries(key);
    Map<String, String> f = new HashMap<>();
    raw.forEach((k, v) -> f.put(String.valueOf(k), String.valueOf(v)));
    return f;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
