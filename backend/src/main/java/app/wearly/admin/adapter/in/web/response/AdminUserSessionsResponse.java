package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminUserSessionsDto;
import app.wearly.admin.application.port.in.dto.LoginEventDto;
import app.wearly.common.web.Times;
import java.util.List;

/** GET /admin/users/{id}/sessions: 그 회원의 세션과 최근 로그인 이력 20개 (16 문서 4장) */
public record AdminUserSessionsResponse(
    List<AdminSessionResponse> sessions, List<LoginEvent> events) {

  /**
   * type은 LOGIN | LOGOUT | REFRESH_REUSED | FORCED_LOGOUT | LOGIN_FAILED | MFA_* | SSO_LOGIN |
   * SSO_DENIED. client·ip·userAgent는 없을 수 있다. ssoProvider·ssoAmr은 SSO 로그인·거부에만
   */
  public record LoginEvent(
      String type,
      String sessionId,
      String client,
      String ip,
      String userAgent,
      String createdAt,
      String ssoProvider,
      String ssoAmr) {

    static LoginEvent from(LoginEventDto e) {
      return new LoginEvent(
          e.type(),
          e.sessionId(),
          e.client(),
          e.ip(),
          e.userAgent(),
          Times.iso(e.createdAt()),
          e.ssoProvider(),
          e.ssoAmr());
    }
  }

  public static AdminUserSessionsResponse from(AdminUserSessionsDto d) {
    return new AdminUserSessionsResponse(
        d.sessions().stream().map(AdminSessionResponse::from).toList(),
        d.events().stream().map(LoginEvent::from).toList());
  }
}
