package app.wearly.auth.adapter.out.valkey;

/**
 * Valkey 키 이름 (16 문서 3장). 접두사 {@code wearly:}. 시각 값은 epoch 밀리초 문자열이다.
 *
 * <ul>
 *   <li>{@code wearly:rt:{tokenHash}} 해시: userId, sessionId, status(ACTIVE·ROTATED·REVOKED),
 *       replacedBy, createdAt. TTL = Refresh 만료 (회전·폐기돼도 만료까지 남겨 재사용을 감지)
 *   <li>{@code wearly:session:{sessionId}} 해시: userId, currentHash, client, userAgent, ip,
 *       createdAt, lastRefreshAt, lastSeenAt. TTL = 마지막 회전 + Refresh 수명
 *   <li>{@code wearly:user:{userId}:sessions} 집합: sessionId. TTL = 가장 늦은 세션 만료
 *   <li>{@code wearly:sessions} 정렬 집합: sessionId, score = lastSeenAt (관리자 목록 정렬·페이징)
 *   <li>{@code wearly:sessions:expiry} 정렬 집합: sessionId, score = 세션 만료 시각 (읽을 때 만료된 멤버를 두 인덱스에서
 *       지운다)
 * </ul>
 */
final class ValkeyKeys {

  static final String PREFIX = "wearly:";
  static final String INDEX = PREFIX + "sessions";
  static final String EXPIRY = PREFIX + "sessions:expiry";

  private ValkeyKeys() {}

  static String token(String tokenHash) {
    return PREFIX + "rt:" + tokenHash;
  }

  static String session(String sessionId) {
    return PREFIX + "session:" + sessionId;
  }

  static String userSessions(long userId) {
    return PREFIX + "user:" + userId + ":sessions";
  }
}
