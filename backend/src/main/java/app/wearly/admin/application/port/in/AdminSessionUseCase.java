package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminSessionDto;
import app.wearly.admin.application.port.in.dto.AdminUserSessionsDto;
import app.wearly.admin.application.port.in.dto.AuthEventLogDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;

/** 로그인 사용자 보기·강제 로그아웃 (16 문서 4장). currentSessionId = 요청한 관리자의 세션(Access 토큰 sid) */
public interface AdminSessionUseCase {

  /** lastSeenAt 최신순. q: 이메일·이름 부분 일치(대소문자 무시), client: APP | ADMIN | WEB (틀리면 400) */
  PageDto<AdminSessionDto> list(String currentSessionId, String q, String client, PagingDto paging);

  /** 회원의 세션 + 최근 로그인 이력 20개. 없는 회원이면 404 */
  AdminUserSessionsDto userSessions(String currentSessionId, long userId);

  /** 세션 하나 끊기 (SESSION_REVOKE). 자기 현재 세션이면 400, 없으면 404 */
  void revokeSession(long adminId, String currentSessionId, String sessionId);

  /** 회원의 모든 세션 끊기 (USER_SESSIONS_REVOKE). 자기 자신이면 400, 없는 회원이면 404 */
  void revokeUserSessions(long adminId, long userId);

  /**
   * 관리자 로그인 기록 (22 문서): 지금 관리자인 회원의 이벤트 + 관리자 사이트(client ADMIN)에서 일어난 이벤트, 최신순. userId(숫자),
   * type(8종), from·to(YYYY-MM-DD 한국 날짜 — to는 그날 포함 — 또는 ISO-8601 시각) 중 틀린 값은 400 VALIDATION_FAILED
   */
  PageDto<AuthEventLogDto> authEvents(
      String userId, String type, String from, String to, PagingDto paging);
}
