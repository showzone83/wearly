package app.wearly.support;

import app.wearly.auth.application.port.out.MfaChallengeStorePort;
import app.wearly.auth.application.port.out.MfaPort;
import app.wearly.auth.application.port.out.MfaSecretCipherPort;
import app.wearly.auth.application.port.out.TotpPort;
import app.wearly.auth.domain.MfaEnrollment;
import app.wearly.auth.domain.PendingMfa;
import app.wearly.auth.domain.SealedMfaSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 서비스 단위 테스트용 2단계 인증 저장소·암호화·TOTP (메모리, TTL 없음). 가짜 TOTP는 "0"으로 시작하는 6자리 숫자만 맞는 코드로 보고 그 숫자를 단계로 쓴다
 * (예: "000101" → 101단계). 재사용 막기 규칙은 실제와 같다: 마지막 사용 단계 이하는 인정하지 않는다.
 */
public class InMemoryMfa implements MfaPort, MfaChallengeStorePort {

  public final Map<Long, MfaEnrollment> enrollments = new LinkedHashMap<>();
  public final Map<Long, Map<String, Boolean>> recoveryCodes = new HashMap<>();
  public final Map<String, PendingMfa> pending = new LinkedHashMap<>();
  public final Map<Long, SealedMfaSecret> setups = new HashMap<>();
  public final Map<Long, Long> failures = new HashMap<>();
  public final Map<Long, Duration> locks = new HashMap<>();

  /** 암호화는 하지 않고 바이트만 옮긴다 */
  public final MfaSecretCipherPort cipher =
      new MfaSecretCipherPort() {
        @Override
        public boolean enabled() {
          return true;
        }

        @Override
        public SealedMfaSecret seal(String plaintext, String aad) {
          return new SealedMfaSecret(
              (aad + "|" + plaintext).getBytes(StandardCharsets.UTF_8), new byte[12], 1);
        }

        @Override
        public String open(SealedMfaSecret sealed, String aad) {
          String s = new String(sealed.ciphertext(), StandardCharsets.UTF_8);
          return s.substring(s.indexOf('|') + 1);
        }
      };

  public final TotpPort totp =
      new TotpPort() {
        @Override
        public String newSecret() {
          return "JBSWY3DPEHPK3PXP";
        }

        @Override
        public OptionalLong verify(
            String base32Secret, String code, Instant now, Long lastUsedStep) {
          if (code == null || !code.matches("0\\d{5}")) {
            return OptionalLong.empty();
          }
          long step = Long.parseLong(code);
          return lastUsedStep != null && step <= lastUsedStep
              ? OptionalLong.empty()
              : OptionalLong.of(step);
        }

        @Override
        public String otpauthUrl(String issuer, String account, String base32Secret) {
          return "otpauth://totp/" + issuer + ":" + account + "?secret=" + base32Secret;
        }
      };

  // ── MfaPort ──

  @Override
  public Optional<MfaEnrollment> find(long userId) {
    return Optional.ofNullable(enrollments.get(userId));
  }

  @Override
  public boolean exists(long userId) {
    return enrollments.containsKey(userId);
  }

  @Override
  public void save(long userId, SealedMfaSecret secret, long usedStep, Instant enrolledAt) {
    enrollments.put(userId, new MfaEnrollment(userId, secret, usedStep, enrolledAt));
  }

  @Override
  public boolean useStep(long userId, long step) {
    MfaEnrollment e = enrollments.get(userId);
    if (e == null || (e.lastUsedStep() != null && e.lastUsedStep() >= step)) {
      return false;
    }
    enrollments.put(userId, new MfaEnrollment(userId, e.secret(), step, e.enrolledAt()));
    return true;
  }

  @Override
  public boolean delete(long userId) {
    recoveryCodes.remove(userId);
    return enrollments.remove(userId) != null;
  }

  @Override
  public void replaceRecoveryCodes(long userId, List<String> codeHashes, Instant createdAt) {
    Map<String, Boolean> codes = new LinkedHashMap<>();
    codeHashes.forEach(h -> codes.put(h, false));
    recoveryCodes.put(userId, codes);
  }

  @Override
  public boolean useRecoveryCode(long userId, String codeHash, Instant usedAt) {
    Map<String, Boolean> codes = recoveryCodes.getOrDefault(userId, Map.of());
    if (!Boolean.FALSE.equals(codes.get(codeHash))) {
      return false;
    }
    codes.put(codeHash, true);
    return true;
  }

  @Override
  public int countRecoveryCodes(long userId) {
    return (int)
        recoveryCodes.getOrDefault(userId, Map.of()).values().stream().filter(u -> !u).count();
  }

  // ── MfaChallengeStorePort ──

  @Override
  public void savePending(String tokenHash, PendingMfa p, Duration ttl) {
    pending.put(tokenHash, p);
  }

  @Override
  public Optional<PendingMfa> findPending(String tokenHash) {
    return Optional.ofNullable(pending.get(tokenHash));
  }

  @Override
  public void deletePending(String tokenHash) {
    pending.remove(tokenHash);
  }

  @Override
  public void saveSetupSecret(long userId, SealedMfaSecret secret, Duration ttl) {
    setups.put(userId, secret);
  }

  @Override
  public Optional<SealedMfaSecret> findSetupSecret(long userId) {
    return Optional.ofNullable(setups.get(userId));
  }

  @Override
  public void deleteSetupSecret(long userId) {
    setups.remove(userId);
  }

  @Override
  public long addFailure(long userId, Duration ttl) {
    return failures.merge(userId, 1L, Long::sum);
  }

  @Override
  public void clearFailures(long userId) {
    failures.remove(userId);
  }

  @Override
  public void lock(long userId, Duration ttl) {
    locks.put(userId, ttl);
  }

  @Override
  public Optional<Duration> lockRemaining(long userId) {
    return Optional.ofNullable(locks.get(userId));
  }
}
