package app.wearly.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 세션 (16 문서 3장). 로그인(가입) 한 번이 세션 하나이고, Refresh 토큰을 회전해도 같은 세션이다. Access 토큰의 {@code sid} 클레임이 세션
 * ID다.
 *
 * @param lastRefreshAt 마지막 토큰 갱신 시각 (갱신한 적 없으면 null)
 * @param lastSeenAt 마지막 활동 시각 (로그인·갱신·인증된 요청. 같은 세션은 {@link #SEEN_WRITE_INTERVAL}에 한 번만 쓴다)
 */
public record LoginSession(
    String id,
    long userId,
    ClientKind client,
    String userAgent,
    String ip,
    Instant createdAt,
    Instant lastRefreshAt,
    Instant lastSeenAt) {

  /** lastSeenAt을 다시 쓰는 최소 간격 (매 요청 쓰지 않게) */
  public static final Duration SEEN_WRITE_INTERVAL = Duration.ofMinutes(1);

  public static String newId() {
    return UUID.randomUUID().toString();
  }

  /** 새 로그인 세션 */
  public static LoginSession start(long userId, ClientInfo client, Instant now) {
    return new LoginSession(
        newId(), userId, client.client(), client.userAgent(), client.ip(), now, null, now);
  }
}
