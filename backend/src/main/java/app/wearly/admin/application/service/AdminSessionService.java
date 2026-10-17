package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminSessionUseCase;
import app.wearly.admin.application.port.in.dto.AdminSessionDto;
import app.wearly.admin.application.port.in.dto.AdminUserSessionsDto;
import app.wearly.admin.application.port.in.dto.LoginEventDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.UserBriefRowDto;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.admin.domain.SearchText;
import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.auth.application.port.in.dto.SessionDto;
import app.wearly.auth.application.port.in.dto.SessionSliceDto;
import app.wearly.common.error.ApiException;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 사용자 보기·강제 로그아웃 (16 문서 4장). 세션은 auth 컨텍스트(Valkey)에, 회원 이메일·이름·역할은 DB(MyBatis 조회)에 있어 이 서비스가
 * 합친다. 검색어·기기 필터가 있으면 살아 있는 세션 전체를 읽어 거르고, 없으면 세션 인덱스(lastSeenAt 최신순)에서 한 쪽만 읽는다.
 */
@Service
public class AdminSessionService implements AdminSessionUseCase {

  static final Set<String> CLIENTS = Set.of("APP", "ADMIN", "WEB");
  static final int RECENT_EVENTS = 20;

  private final SessionUseCase sessions;
  private final UserUseCase users;
  private final AdminQueryPort queries;
  private final RecordAuditPort audit;

  public AdminSessionService(
      SessionUseCase sessions, UserUseCase users, AdminQueryPort queries, RecordAuditPort audit) {
    this.sessions = sessions;
    this.users = users;
    this.queries = queries;
    this.audit = audit;
  }

  @Override
  @Transactional(readOnly = true)
  public PageDto<AdminSessionDto> list(
      String currentSessionId, String q, String client, PagingDto paging) {
    String cl = SearchText.blankToNull(client);
    if (cl != null && !CLIENTS.contains(cl)) {
      throw ApiException.validation("client", "APP, ADMIN, WEB 중 하나여야 해요.");
    }
    String query = SearchText.blankToNull(q);
    if (query == null && cl == null) {
      SessionSliceDto slice = sessions.recentSessions(paging.offset(), paging.size());
      return new PageDto<>(
          join(slice.items(), currentSessionId), paging.page(), paging.size(), slice.total());
    }
    List<SessionDto> all =
        sessions.allSessions().stream().filter(s -> cl == null || cl.equals(s.client())).toList();
    List<AdminSessionDto> matched = join(all, currentSessionId);
    if (query != null) {
      String needle = query.toLowerCase(Locale.ROOT);
      matched = matched.stream().filter(s -> matches(s, needle)).toList();
    }
    int from = (int) Math.min(paging.offset(), matched.size());
    int to = Math.min(from + paging.size(), matched.size());
    return new PageDto<>(matched.subList(from, to), paging.page(), paging.size(), matched.size());
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserSessionsDto userSessions(String currentSessionId, long userId) {
    users.get(userId);
    List<AdminSessionDto> list = join(sessions.userSessions(userId), currentSessionId);
    List<LoginEventDto> events =
        queries.findAuthEvents(userId, RECENT_EVENTS).stream()
            .map(
                e ->
                    new LoginEventDto(
                        e.type(), e.sessionId(), e.client(), e.ip(), e.userAgent(), e.createdAt()))
            .toList();
    return new AdminUserSessionsDto(list, events);
  }

  @Override
  @Transactional
  public void revokeSession(long adminId, String currentSessionId, String sessionId) {
    if (sessionId != null && sessionId.equals(currentSessionId)) {
      throw ApiException.validation("sessionId", "지금 쓰고 있는 세션은 끊을 수 없어요. 로그아웃을 해 주세요.");
    }
    SessionDto target = sessions.findSession(sessionId).orElseThrow(ApiException::notFound);
    if (!sessions.endSession(sessionId)) {
      throw ApiException.notFound();
    }
    AdminSessionDto view = join(List.of(target), currentSessionId).getFirst();
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("userId", Long.toString(view.userId()));
    before.put("email", view.email());
    before.put("client", view.client());
    before.put("ip", view.ip());
    before.put("createdAt", view.createdAt());
    before.put("lastSeenAt", view.lastSeenAt());
    audit.record(adminId, "SESSION_REVOKE", AuditTargets.SESSION, sessionId, before, null);
  }

  @Override
  @Transactional
  public void revokeUserSessions(long adminId, long userId) {
    if (userId == adminId) {
      throw ApiException.validation("id", "자기 자신의 세션은 모두 끊을 수 없어요.");
    }
    UserAccountDto user = users.get(userId);
    int ended = sessions.endUserSessions(userId);
    Map<String, Object> before = new LinkedHashMap<>();
    before.put("email", user.email());
    before.put("sessionCount", ended);
    audit.record(
        adminId,
        "USER_SESSIONS_REVOKE",
        AuditTargets.USER,
        Long.toString(userId),
        before,
        Map.<String, Object>of("sessionCount", 0));
  }

  /** 세션에 회원 이메일·이름·역할을 붙인다 (순서 유지) */
  private List<AdminSessionDto> join(List<SessionDto> list, String currentSessionId) {
    Set<Long> ids = list.stream().map(SessionDto::userId).collect(Collectors.toSet());
    Map<Long, UserBriefRowDto> briefs =
        queries.findUserBriefs(ids).stream()
            .collect(Collectors.toMap(UserBriefRowDto::id, Function.identity()));
    return list.stream()
        .map(
            s -> {
              UserBriefRowDto u = briefs.get(s.userId());
              return new AdminSessionDto(
                  s.sessionId(),
                  s.userId(),
                  u == null ? null : u.email(),
                  u == null ? null : u.name(),
                  u == null ? null : u.role(),
                  s.client(),
                  s.userAgent(),
                  s.ip(),
                  s.createdAt(),
                  s.lastRefreshAt(),
                  s.lastSeenAt(),
                  s.sessionId().equals(currentSessionId));
            })
        .toList();
  }

  private static boolean matches(AdminSessionDto s, String needle) {
    return contains(s.email(), needle) || contains(s.name(), needle);
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }
}
