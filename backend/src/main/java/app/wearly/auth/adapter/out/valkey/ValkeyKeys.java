package app.wearly.auth.adapter.out.valkey;

/**
 * Valkey 키 이름 (16 문서 3장). 접두사 {@code wearly:}. 시각 값은 epoch 밀리초 문자열이다.
 *
 * <ul>
 *   <li>{@code wearly:rt:{tokenHash}} 해시: userId, sessionId, status(ACTIVE·ROTATED·REVOKED),
 *       replacedBy, createdAt. TTL = Refresh 만료 (회전·폐기돼도 만료까지 남겨 재사용을 감지)
 *   <li>{@code wearly:session:{sessionId}} 해시: userId, currentHash, client, userAgent, ip,
 *       createdAt, lastRefreshAt, lastSeenAt, mfa(2단계 인증을 확인한 세션이면 "1"). TTL = 마지막 회전 + Refresh 수명
 *   <li>{@code wearly:mfa:pending:*}·{@code wearly:mfa:setup:*}·{@code wearly:mfa:fail:*}·{@code
 *       wearly:mfa:lock:*}: 관리자 2단계 인증 (22 문서, ValkeyMfaStoreAdapter)
 *   <li>{@code wearly:sso:state:*}·{@code wearly:sso:grant:*}: 관리자 사이트 SSO (22 문서 4.2,
 *       ValkeySsoStoreAdapter)
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

  /** MFA 대기 토큰 해시: userId, client, userAgent, ip, createdAt. TTL 5분 */
  static String mfaPending(String tokenHash) {
    return PREFIX + "mfa:pending:" + tokenHash;
  }

  /** 등록 중인 TOTP 비밀(암호문) 해시: cipher, nonce, keyVersion. TTL 10분 */
  static String mfaSetup(long userId) {
    return PREFIX + "mfa:setup:" + userId;
  }

  /** 틀린 횟수 (INCR). TTL = 마지막 실패에서 15분 */
  static String mfaFailures(long userId) {
    return PREFIX + "mfa:fail:" + userId;
  }

  /** SSO 시작 → 콜백 값 (문자열, form 인코딩). TTL 5분, GETDEL로 한 번만 꺼낸다 */
  static String ssoState(String stateHash) {
    return PREFIX + "sso:state:" + stateHash;
  }

  /** SSO 콜백 → 교환 코드 값 (문자열, form 인코딩). TTL 1분, GETDEL로 한 번만 꺼낸다 */
  static String ssoGrant(String codeHash) {
    return PREFIX + "sso:grant:" + codeHash;
  }

  /** 잠금 표시. TTL 15분 */
  static String mfaLock(long userId) {
    return PREFIX + "mfa:lock:" + userId;
  }
}
