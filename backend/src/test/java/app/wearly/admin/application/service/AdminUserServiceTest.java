package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.support.FakeSessions;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 회원 정지·역할 변경 규칙과 감사 기록 (가짜 포트, Spring 없이) */
class AdminUserServiceTest {

  record Audit(
      Long adminId,
      String action,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after) {}

  final FakeUsers users = new FakeUsers();
  final List<Audit> audits = new ArrayList<>();

  final FakeSessions sessions = new FakeSessions();

  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) ->
          audits.add(new Audit(adminId, action, targetId, before, after));

  /** 목록 조회는 FakeUsers에서 id로 한 줄만 돌려준다 */
  final AdminQueryPort queries =
      new UnsupportedAdminQueries() {
        @Override
        public List<UserListRowDto> findUsers(
            UserFilter filter, Instant todayFrom, Instant todayTo, int limit, long offset) {
          return users.find(filter.id()).stream()
              .map(
                  u ->
                      new UserListRowDto(
                          u.id(),
                          u.email(),
                          u.name(),
                          u.role(),
                          u.status(),
                          u.createdAt(),
                          0,
                          0,
                          0))
              .toList();
        }

        @Override
        public long countUsers(UserFilter filter) {
          return users.find(filter.id()).isPresent() ? 1 : 0;
        }
      };

  final AdminUserService service =
      new AdminUserService(
          queries,
          users,
          sessions,
          audit,
          Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC));

  final UserAccountDto admin =
      users.add("admin@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);
  final UserAccountDto member =
      users.add("member@wearly.test", UserAccountDto.ROLE_USER, UserAccountDto.ACTIVE);

  @Test
  void 정지하면_세션을_모두_끊고_전후_상태를_감사_기록한다() {
    AdminUserDto view = service.patch(admin.id(), member.id(), patch("SUSPENDED", null));

    assertThat(view.status()).isEqualTo("SUSPENDED");
    assertThat(sessions.endedUsers).containsExactly(member.id());
    assertThat(audits)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.adminId()).isEqualTo(admin.id());
              assertThat(a.action()).isEqualTo("USER_UPDATE");
              assertThat(a.targetId()).isEqualTo(Long.toString(member.id()));
              assertThat(a.before())
                  .containsEntry("status", "ACTIVE")
                  .containsEntry("role", "USER");
              assertThat(a.after()).containsEntry("status", "SUSPENDED");
            });

    // 이미 정지된 회원을 다시 정지하면 세션 끊기는 하지 않는다
    service.patch(admin.id(), member.id(), patch("SUSPENDED", null));
    assertThat(sessions.endedUsers).hasSize(1);
  }

  @Test
  void 자기_자신은_정지하거나_관리자_역할을_내릴_수_없다() {
    assertThatThrownBy(() -> service.patch(admin.id(), admin.id(), patch("SUSPENDED", "USER")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(
                        new FieldError("status", "자기 자신은 정지할 수 없어요."),
                        new FieldError("role", "자기 자신의 관리자 역할은 내릴 수 없어요.")));
    assertThat(users.get(admin.id()).status()).isEqualTo("ACTIVE");
    assertThat(audits).isEmpty();
    assertThat(sessions.endedUsers).isEmpty();
  }

  @Test
  void 모르는_값은_필드별로_거부하고_아무것도_바꾸지_않는다() {
    assertThatThrownBy(() -> service.patch(admin.id(), member.id(), patch("BANNED", "ROOT")))
        .isInstanceOfSatisfying(
            ApiException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("status", "role"));
    assertThat(users.get(member.id())).isEqualTo(member);
  }

  @Test
  void 역할만_바꾸면_토큰은_그대로_둔다() {
    AdminUserDto view = service.patch(admin.id(), member.id(), patch(null, "ADMIN"));

    assertThat(view.role()).isEqualTo("ADMIN");
    assertThat(view.status()).isEqualTo("ACTIVE");
    assertThat(sessions.endedUsers).isEmpty();
  }

  private static PatchUserDto patch(String status, String role) {
    return new PatchUserDto(
        status == null ? Patch.absent() : Patch.of(status),
        role == null ? Patch.absent() : Patch.of(role));
  }
}
