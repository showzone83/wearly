package app.wearly.auth.domain;

import java.time.Instant;

/**
 * Refresh 토큰 (순수 도메인 모델, 16 문서 3장). 원문은 저장하지 않고 SHA-256 hex만 둔다. 로그인 한 번이 세션 하나이고, 회전할 때마다 같은 세션
 * 안에서 새 토큰이 생긴다. 회전·폐기된 토큰도 만료까지 남겨 이미 회전된 토큰이 다시 오면 재사용으로 본다. 만료는 저장소의 TTL이 맡는다(만료되면 없는 토큰).
 *
 * @param replacedBy 회전으로 바뀐 다음 토큰의 해시 (회전 전이면 null)
 */
public record RefreshToken(
    String tokenHash,
    long userId,
    String sessionId,
    Status status,
    String replacedBy,
    Instant createdAt) {

  /** 저장된 토큰 상태 */
  public enum Status {
    /** 지금 쓸 수 있는 토큰 (세션마다 하나) */
    ACTIVE,
    /** 회전으로 바뀐 토큰. 다시 오면 재사용 */
    ROTATED,
    /** 로그아웃·강제 로그아웃·정지로 끊긴 세션의 마지막 토큰 */
    REVOKED
  }

  /** 이 토큰으로 갱신해도 되는지 */
  public enum Check {
    /** 회전해도 된다 */
    OK,
    /** 폐기됐거나(로그아웃·강제 로그아웃·정지) 없음·만료 → 401 REFRESH_INVALID */
    INVALID,
    /** 이미 회전된 토큰의 재사용 → 세션 전체 끊기, 401 REFRESH_REUSED */
    REUSED
  }

  /** 새 세션의 첫 토큰이나 회전으로 만든 다음 토큰 */
  public static RefreshToken issue(String tokenHash, long userId, String sessionId, Instant now) {
    return new RefreshToken(tokenHash, userId, sessionId, Status.ACTIVE, null, now);
  }

  public Check check() {
    return switch (status) {
      case ACTIVE -> Check.OK;
      case ROTATED -> Check.REUSED;
      case REVOKED -> Check.INVALID;
    };
  }
}
