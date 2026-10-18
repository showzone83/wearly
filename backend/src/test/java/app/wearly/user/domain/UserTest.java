package app.wearly.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;

/** H2: 회원 이름·상태·역할 규칙은 도메인이 최종 보증한다 (Spring 없이) */
class UserTest {

  @Test
  void 새_회원은_ACTIVE이고_이름은_1_20자() {
    User u = User.newUser("a@wearly.test", "웨어리", UserRole.USER);
    assertThat(u.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(u.isActive()).isTrue();
    assertThat(u.isAdmin()).isFalse();
    assertThatThrownBy(() -> User.newUser("a@wearly.test", "가".repeat(21), UserRole.USER))
        .isInstanceOf(DomainValidationException.class)
        .satisfies(
            e ->
                assertThat(((DomainValidationException) e).errors())
                    .containsExactly(FieldError.tooLong("name", 20)));
    assertThatThrownBy(() -> u.rename("  "))
        .isInstanceOf(DomainValidationException.class)
        .satisfies(
            e ->
                assertThat(((DomainValidationException) e).errors())
                    .containsExactly(FieldError.required("name")));
  }

  @Test
  void 상태와_역할은_정해진_값만() {
    User u = User.newUser("a@wearly.test", "웨어리", UserRole.USER);
    u.changeAccess("SUSPENDED", "ADMIN");
    assertThat(u.status()).isEqualTo(UserStatus.SUSPENDED);
    assertThat(u.role()).isEqualTo(UserRole.ADMIN);
    assertThatThrownBy(() -> u.changeAccess("DELETED", "admin"))
        .isInstanceOf(DomainValidationException.class)
        .satisfies(
            e ->
                assertThat(((DomainValidationException) e).errors())
                    .containsExactly(
                        FieldError.notAllowed("status", List.of("ACTIVE", "SUSPENDED")),
                        FieldError.notAllowed("role", List.of("USER", "ADMIN"))));
    // 틀리면 아무것도 바꾸지 않는다
    assertThat(u.status()).isEqualTo(UserStatus.SUSPENDED);
    assertThat(u.role()).isEqualTo(UserRole.ADMIN);
  }
}
