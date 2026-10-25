package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.auth.application.port.in.AccountUseCase;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 시작 시 관리자 만들기 (22 문서 4.1): 관리자가 없을 때만, 같은 이메일의 일반 회원은 올리지 않는다 */
class AdminAccountServiceTest {

  final FakeUsers users = new FakeUsers();
  final List<String> audits = new ArrayList<>();
  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) -> audits.add(action);

  final AccountUseCase accounts =
      new AccountUseCase() {
        @Override
        public Optional<Long> findUserIdByEmail(String email) {
          return users.rows.values().stream()
              .filter(u -> u.email().equals(email))
              .map(UserAccountDto::id)
              .findFirst();
        }

        @Override
        public long createAccount(String email, String rawPassword, String name, String role) {
          return users.create(email, name, role).id();
        }

        @Override
        public boolean passwordMatches(long userId, String rawPassword) {
          throw new UnsupportedOperationException();
        }
      };

  final AdminAccountService service = new AdminAccountService(accounts, users, audit);

  @Test
  void 관리자가_없으면_설정_계정을_만든다() {
    Optional<Long> id = service.ensureAdmin(" Admin@Wearly.App ", "secret123", null);

    assertThat(id).isPresent();
    UserAccountDto created = users.get(id.get());
    assertThat(created.email()).isEqualTo("admin@wearly.app");
    assertThat(created.role()).isEqualTo(UserAccountDto.ROLE_ADMIN);
    assertThat(created.name()).isEqualTo("관리자");
    assertThat(audits).containsExactly("ADMIN_BOOTSTRAP_CREATE");
  }

  @Test
  void 같은_이메일의_일반_회원은_올리지_않는다() {
    UserAccountDto squatter =
        users.add("admin@wearly.app", UserAccountDto.ROLE_USER, UserAccountDto.ACTIVE);

    assertThat(service.ensureAdmin("admin@wearly.app", "secret123", "관리자")).isEmpty();

    assertThat(users.get(squatter.id()).role()).isEqualTo(UserAccountDto.ROLE_USER);
    assertThat(users.countAdmins()).isZero();
    assertThat(audits).isEmpty();
  }

  @Test
  void 관리자가_있으면_설정을_쓰지_않는다() {
    users.add("boss@wearly.app", UserAccountDto.ROLE_ADMIN, UserAccountDto.SUSPENDED);

    assertThat(service.ensureAdmin("admin@wearly.app", "secret123", "관리자")).isEmpty();

    assertThat(users.rows).hasSize(1);
    assertThat(audits).isEmpty();
  }

  @Test
  void 로그의_이메일은_앞_2글자만_보인다() {
    assertThat(AdminAccountService.mask("admin@wearly.app")).isEqualTo("ad***@***");
    assertThat(AdminAccountService.mask("a@x.io")).isEqualTo("a***@***");
  }
}
