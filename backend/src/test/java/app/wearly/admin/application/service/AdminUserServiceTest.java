package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.admin.application.port.in.dto.AdminUserDto;
import app.wearly.admin.application.port.in.dto.PatchUserDto;
import app.wearly.admin.application.port.out.AdminQueryPort;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.application.port.out.dto.UserListRowDto;
import app.wearly.auth.application.port.in.MfaUseCase;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.MfaSetupDto;
import app.wearly.auth.application.port.in.dto.MfaStatusDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyDto;
import app.wearly.auth.application.port.in.dto.MfaVerifyResultDto;
import app.wearly.common.application.Patch;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
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

  /** 2단계 인증 초기화만 쓴다 (지운 회원을 기록) */
  final List<Long> mfaRemoved = new ArrayList<>();

  final MfaUseCase mfa =
      new MfaUseCase() {
        @Override
        public MfaStatusDto remove(long userId) {
          mfaRemoved.add(userId);
          return new MfaStatusDto(true, Instant.EPOCH, 7, true);
        }

        @Override
        public MfaSetupDto setup(String mfaToken) {
          throw new UnsupportedOperationException();
        }

        @Override
        public MfaVerifyResultDto verify(MfaVerifyDto command, ClientDto client) {
          throw new UnsupportedOperationException();
        }

        @Override
        public MfaStatusDto status(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public MfaSetupDto startSetup(long userId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<String> enable(long userId, String sessionId, String code) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int reset(
            long userId,
            String sessionId,
            String currentPassword,
            String code,
            String recoveryCode) {
          throw new UnsupportedOperationException();
        }
      };

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
                          0,
                          null,
                          null,
                          null,
                          null,
                          false,
                          null,
                          null,
                          null,
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
          mfa,
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
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(
                        new FieldError("status", FieldErrorCode.SELF_NOT_ALLOWED),
                        new FieldError("role", FieldErrorCode.SELF_NOT_ALLOWED)));
    assertThat(users.get(admin.id()).status()).isEqualTo("ACTIVE");
    assertThat(audits).isEmpty();
    assertThat(sessions.endedUsers).isEmpty();
  }

  @Test
  void 모르는_값은_필드별로_거부하고_아무것도_바꾸지_않는다() {
    assertThatThrownBy(() -> service.patch(admin.id(), member.id(), patch("BANNED", "ROOT")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("status", "role"));
    assertThat(users.get(member.id())).isEqualTo(member);
  }

  @Test
  void 관리자로_지정하면_세션은_그대로_두고_USER_ROLE_CHANGE를_남긴다() {
    AdminUserDto view = service.patch(admin.id(), member.id(), patch(null, "ADMIN"));

    assertThat(view.role()).isEqualTo("ADMIN");
    assertThat(view.status()).isEqualTo("ACTIVE");
    assertThat(sessions.endedUsers).isEmpty();
    assertThat(audits)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.action()).isEqualTo("USER_ROLE_CHANGE");
              assertThat(a.before()).containsEntry("role", "USER");
              assertThat(a.after()).containsEntry("role", "ADMIN");
            });
  }

  @Test
  void 관리자를_해제하면_그_회원의_세션을_모두_끊는다() {
    UserAccountDto other =
        users.add("other@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);

    service.patch(admin.id(), other.id(), patch(null, "USER"));

    assertThat(users.get(other.id()).role()).isEqualTo("USER");
    assertThat(sessions.endedUsers).containsExactly(other.id());
    assertThat(audits).extracting(Audit::action).containsExactly("USER_ROLE_CHANGE");
  }

  @Test
  void 쓸_수_있는_관리자가_남지_않으면_LAST_ADMIN() {
    // 요청한 관리자가 그사이 정지된 상황 (동시 요청): 남는 ACTIVE 관리자가 없다
    UserAccountDto other =
        users.add("other@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);
    users.changeAccess(admin.id(), UserAccountDto.SUSPENDED, UserAccountDto.ROLE_ADMIN);

    assertThatThrownBy(() -> service.patch(admin.id(), other.id(), patch(null, "USER")))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
    assertThatThrownBy(() -> service.patch(admin.id(), other.id(), patch("SUSPENDED", null)))
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LAST_ADMIN));
    assertThat(users.get(other.id()).role()).isEqualTo("ADMIN");
    assertThat(sessions.endedUsers).isEmpty();
    assertThat(audits).isEmpty();
  }

  @Test
  void 정지된_회원은_관리자로_지정할_수_없다() {
    users.changeAccess(member.id(), UserAccountDto.SUSPENDED, UserAccountDto.ROLE_USER);

    assertThatThrownBy(() -> service.patch(admin.id(), member.id(), patch(null, "ADMIN")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("role", FieldErrorCode.CONFLICT)));
    assertThat(users.get(member.id()).role()).isEqualTo("USER");
  }

  @Test
  void 다른_관리자의_2단계_인증을_초기화하면_세션을_끊고_기록한다() {
    UserAccountDto other =
        users.add("other@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);

    service.resetMfa(admin.id(), other.id());

    assertThat(mfaRemoved).containsExactly(other.id());
    assertThat(sessions.endedUsers).containsExactly(other.id());
    assertThat(audits)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.action()).isEqualTo("USER_MFA_RESET");
              assertThat(a.before()).containsEntry("enrolled", true);
              assertThat(a.after()).containsEntry("enrolled", false);
            });

    assertThatThrownBy(() -> service.resetMfa(admin.id(), admin.id()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .containsExactly(new FieldError("id", FieldErrorCode.SELF_NOT_ALLOWED)));
    assertThat(mfaRemoved).hasSize(1);
  }

  private static PatchUserDto patch(String status, String role) {
    return new PatchUserDto(
        status == null ? Patch.absent() : Patch.of(status),
        role == null ? Patch.absent() : Patch.of(role));
  }
}
