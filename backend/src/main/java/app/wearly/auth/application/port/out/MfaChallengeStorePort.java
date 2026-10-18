package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.PendingMfa;
import app.wearly.auth.domain.SealedMfaSecret;
import java.time.Duration;
import java.util.Optional;

/**
 * 2단계 인증의 짧게 사는 상태 (Valkey). 만료는 저장소 TTL이 맡는다.
 *
 * <ul>
 *   <li>MFA 대기 토큰 {@code wearly:mfa:pending:{tokenHash}} (5분)
 *   <li>등록 중인 비밀 {@code wearly:mfa:setup:{userId}} (10분, 암호화한 값)
 *   <li>틀린 횟수 {@code wearly:mfa:fail:{userId}} (마지막 실패에서 15분)
 *   <li>잠금 {@code wearly:mfa:lock:{userId}} (15분)
 * </ul>
 */
public interface MfaChallengeStorePort {

  void savePending(String tokenHash, PendingMfa pending, Duration ttl);

  Optional<PendingMfa> findPending(String tokenHash);

  void deletePending(String tokenHash);

  void saveSetupSecret(long userId, SealedMfaSecret secret, Duration ttl);

  Optional<SealedMfaSecret> findSetupSecret(long userId);

  void deleteSetupSecret(long userId);

  /** 틀린 횟수를 하나 올리고(창 = ttl, 실패마다 연장) 올린 뒤의 값을 돌려준다 */
  long addFailure(long userId, Duration ttl);

  void clearFailures(long userId);

  void lock(long userId, Duration ttl);

  /** 잠겨 있으면 풀리기까지 남은 시간 */
  Optional<Duration> lockRemaining(long userId);
}
