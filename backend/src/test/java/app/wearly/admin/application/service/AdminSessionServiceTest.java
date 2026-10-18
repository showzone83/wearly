package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.admin.application.port.in.dto.AdminSessionDto;
import app.wearly.admin.application.port.in.dto.PageDto;
import app.wearly.admin.application.port.in.dto.PagingDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.AuthEventRowDto;
import app.wearly.admin.application.port.out.dto.UserBriefRowDto;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import app.wearly.support.FakeSessions;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 로그인 사용자 목록(정렬·검색·필터·페이지)과 강제 로그아웃 규칙 (가짜 포트, Spring 없이) */
class AdminSessionServiceTest {

  static final Instant T0 = Instant.parse("2026-10-17T00:00:00Z");

  record Audit(String action, String targetType, String targetId, Map<String, Object> before) {}

  final FakeUsers users = new FakeUsers();
  final FakeSessions sessions = new FakeSessions();
  final List<Audit> audits = new ArrayList<>();

  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) ->
          audits.add(new Audit(action, targetType, targetId, before));

  final AdminQueryPort queries =
      new UnsupportedAdminQueries() {
        @Override
        public List<UserBriefRowDto> findUserBriefs(Collection<Long> ids) {
          return ids.stream()
              .flatMap(id -> users.find(id).stream())
              .map(u -> new UserBriefRowDto(u.id(), u.email(), u.name(), u.role()))
              .toList();
        }

        @Override
        public List<AuthEventRowDto> findAuthEvents(long userId, int limit) {
          return List.of(
              new AuthEventRowDto(1L, "LOGIN", "s-member-app", "APP", "10.0.0.1", "ua", T0));
        }
      };

  final AdminSessionService service = new AdminSessionService(sessions, users, queries, audit);

  final UserAccountDto admin =
      users.add("admin@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);
  final UserAccountDto member =
      users.add("member@wearly.test", UserAccountDto.ROLE_USER, UserAccountDto.ACTIVE);

  {
    sessions.add("s-admin", admin.id(), "ADMIN", T0.plusSeconds(30));
    sessions.add("s-member-app", member.id(), "APP", T0.plusSeconds(20));
    sessions.add("s-member-web", member.id(), "WEB", T0.plusSeconds(10));
  }

  @Test
  void 목록은_lastSeenAt_최신순이고_회원_정보와_현재_세션을_붙인다() {
    PageDto<AdminSessionDto> page = service.list("s-admin", null, null, PagingDto.of(0, 2));

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.items())
        .extracting(AdminSessionDto::sessionId)
        .containsExactly("s-admin", "s-member-app");
    assertThat(page.items().getFirst().current()).isTrue();
    assertThat(page.items().get(1).current()).isFalse();
    assertThat(page.items().get(1).email()).isEqualTo("member@wearly.test");
    assertThat(page.items().get(1).role()).isEqualTo("USER");

    PageDto<AdminSessionDto> next = service.list("s-admin", null, null, PagingDto.of(1, 2));
    assertThat(next.items()).extracting(AdminSessionDto::sessionId).containsExactly("s-member-web");
  }

  @Test
  void 검색어와_기기_필터() {
    assertThat(service.list("s-admin", "MEMBER@", null, PagingDto.of(0, 20)).items())
        .extracting(AdminSessionDto::sessionId)
        .containsExactly("s-member-app", "s-member-web");
    PageDto<AdminSessionDto> web = service.list("s-admin", "member", "WEB", PagingDto.of(0, 20));
    assertThat(web.total()).isEqualTo(1);
    assertThat(web.items()).extracting(AdminSessionDto::sessionId).containsExactly("s-member-web");
    assertThat(service.list("s-admin", "없는사람", null, PagingDto.of(0, 20)).total()).isZero();

    assertThatThrownBy(() -> service.list("s-admin", null, "TV", PagingDto.of(0, 20)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  void 회원_세션과_최근_이력_없는_회원은_404() {
    var r = service.userSessions("s-admin", member.id());
    assertThat(r.sessions()).hasSize(2);
    assertThat(r.events()).singleElement().satisfies(e -> assertThat(e.type()).isEqualTo("LOGIN"));
    assertThatThrownBy(() -> service.userSessions("s-admin", 999))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void 세션_하나_끊기_자기_현재_세션은_400_없으면_404() {
    service.revokeSession(admin.id(), "s-admin", "s-member-web");
    assertThat(sessions.endedSessions).containsExactly("s-member-web");
    assertThat(audits)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.action()).isEqualTo("SESSION_REVOKE");
              assertThat(a.targetType()).isEqualTo("SESSION");
              assertThat(a.targetId()).isEqualTo("s-member-web");
              assertThat(a.before()).containsEntry("email", "member@wearly.test");
            });

    assertThatThrownBy(() -> service.revokeSession(admin.id(), "s-admin", "s-admin"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(() -> service.revokeSession(admin.id(), "s-admin", "s-member-web"))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThat(audits).hasSize(1);
  }

  @Test
  void 회원_전체_끊기_자기_자신은_400() {
    service.revokeUserSessions(admin.id(), member.id());
    assertThat(sessions.endedUsers).containsExactly(member.id());
    assertThat(sessions.userSessions(member.id())).isEmpty();
    assertThat(audits)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.action()).isEqualTo("USER_SESSIONS_REVOKE");
              assertThat(a.targetType()).isEqualTo("USER");
              assertThat(a.before()).containsEntry("sessionCount", 2);
            });

    assertThatThrownBy(() -> service.revokeUserSessions(admin.id(), admin.id()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(() -> service.revokeUserSessions(admin.id(), 999))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }
}
