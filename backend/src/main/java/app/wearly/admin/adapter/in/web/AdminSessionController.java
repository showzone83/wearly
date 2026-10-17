package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.response.AdminSessionResponse;
import app.wearly.admin.adapter.in.web.response.AdminUserSessionsResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.application.port.in.AdminSessionUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.common.web.CurrentSession;
import app.wearly.common.web.CurrentUser;
import app.wearly.common.web.PathIds;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자 보기·강제 로그아웃 API (16 문서 4장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSessionController {

  private final AdminSessionUseCase sessions;

  public AdminSessionController(AdminSessionUseCase sessions) {
    this.sessions = sessions;
  }

  @GetMapping("/sessions")
  public PageResponse<AdminSessionResponse> sessions(
      @CurrentSession String currentSessionId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String client,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        sessions.list(currentSessionId, q, client, PagingDto.of(page, size)),
        AdminSessionResponse::from);
  }

  @GetMapping("/users/{id}/sessions")
  public AdminUserSessionsResponse userSessions(
      @CurrentSession String currentSessionId, @PathVariable String id) {
    return AdminUserSessionsResponse.from(
        sessions.userSessions(currentSessionId, PathIds.parse(id)));
  }

  @DeleteMapping("/sessions/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeSession(
      @CurrentUser long adminId,
      @CurrentSession String currentSessionId,
      @PathVariable String sessionId) {
    sessions.revokeSession(adminId, currentSessionId, sessionId);
  }

  @DeleteMapping("/users/{id}/sessions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeUserSessions(@CurrentUser long adminId, @PathVariable String id) {
    sessions.revokeUserSessions(adminId, PathIds.parse(id));
  }
}
