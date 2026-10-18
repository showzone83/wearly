package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AuthEventLogDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/** GET /admin/auth-events 항목 (22 문서). id·userId는 문자열 */
public record AuthEventLogResponse(
    String id,
    String userId,
    String email,
    String name,
    String role,
    String type,
    String sessionId,
    String client,
    String ip,
    String userAgent,
    String createdAt) {

  public static AuthEventLogResponse from(AuthEventLogDto d) {
    return new AuthEventLogResponse(
        Ids.format(d.id()),
        Ids.format(d.userId()),
        d.email(),
        d.name(),
        d.role(),
        d.type(),
        d.sessionId(),
        d.client(),
        d.ip(),
        d.userAgent(),
        Times.iso(d.createdAt()));
  }
}
