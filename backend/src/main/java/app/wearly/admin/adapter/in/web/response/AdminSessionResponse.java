package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminSessionDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * 로그인 사용자 목록 항목 (16 문서 4장). client는 APP | ADMIN | WEB, current는 요청한 관리자 자신의 세션. lastRefreshAt은 갱신한
 * 적이 없으면 null, email·name·role은 회원이 없으면 null
 */
public record AdminSessionResponse(
    String sessionId,
    String userId,
    String email,
    String name,
    String role,
    String client,
    String userAgent,
    String ip,
    String createdAt,
    String lastRefreshAt,
    String lastSeenAt,
    boolean current) {

  public static AdminSessionResponse from(AdminSessionDto d) {
    return new AdminSessionResponse(
        d.sessionId(),
        Ids.format(d.userId()),
        d.email(),
        d.name(),
        d.role(),
        d.client(),
        d.userAgent(),
        d.ip(),
        Times.iso(d.createdAt()),
        Times.iso(d.lastRefreshAt()),
        Times.iso(d.lastSeenAt()),
        d.current());
  }
}
