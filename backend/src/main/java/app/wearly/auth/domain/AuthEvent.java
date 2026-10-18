package app.wearly.auth.domain;

import java.time.Instant;

/**
 * 회원별 로그인 이력 한 줄 (tb_auth_event, 16 문서 3장). 관리자 강제 로그아웃은 변경 이력(tb_admin_audit_log)에도 따로 남는다.
 *
 * @param client 모르면 null
 */
public record AuthEvent(
    long userId,
    Type type,
    String sessionId,
    ClientKind client,
    String ip,
    String userAgent,
    Instant createdAt) {

  public enum Type {
    /** 로그인·가입 (새 세션) */
    LOGIN,
    /** 회원이 직접 로그아웃 */
    LOGOUT,
    /** 이미 회전된 Refresh 토큰 재사용 → 세션 끊김 */
    REFRESH_REUSED,
    /** 관리자 강제 로그아웃·회원 정지·역할 해제·비밀번호 변경·2단계 인증 초기화로 세션 끊김 */
    FORCED_LOGOUT,
    /** 가입한 이메일로 비밀번호가 틀림 (없는 이메일은 남기지 않는다) */
    LOGIN_FAILED,
    /** 관리자 2단계 인증(TOTP·복구 코드) 성공 */
    MFA_SUCCESS,
    /** 관리자 2단계 인증 코드가 틀림 */
    MFA_FAILED,
    /** 2단계 인증을 여러 번 틀려 잠김 */
    MFA_LOCKED
  }

  /** 요청을 보낸 기기 정보로 */
  public static AuthEvent of(
      long userId, Type type, String sessionId, ClientInfo by, Instant createdAt) {
    return new AuthEvent(userId, type, sessionId, by.client(), by.ip(), by.userAgent(), createdAt);
  }

  /** 끊긴 세션의 기기 정보로 */
  public static AuthEvent of(Type type, LoginSession session, Instant createdAt) {
    return new AuthEvent(
        session.userId(),
        type,
        session.id(),
        session.client(),
        session.ip(),
        session.userAgent(),
        createdAt);
  }
}
