package app.wearly.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.user.domain.Withdrawal.Closer;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 탈퇴 기록·회원 상태 규칙 (doc/27) */
class WithdrawalTest {

  static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
  static final Duration GRACE = Duration.ofDays(30);

  @Test
  void 신청하면_유예_기간_뒤가_삭제_시각이고_하고_싶은_말은_공백을_지운다() {
    Withdrawal w = Withdrawal.request(7L, "PRIVACY", "  걱정돼요 ", NOW, GRACE);
    assertThat(w.reason()).isEqualTo(WithdrawalReason.PRIVACY);
    assertThat(w.reasonText()).isEqualTo("걱정돼요");
    assertThat(w.purgeAfter()).isEqualTo(NOW.plus(GRACE));
    assertThat(w.isOpen()).isTrue();
    assertThat(Withdrawal.request(7L, "OTHER", "   ", NOW, GRACE).reasonText()).isNull();
  }

  @Test
  void 사유는_필수이고_정해진_값만_하고_싶은_말은_200자까지() {
    assertThatThrownBy(() -> Withdrawal.request(7L, null, null, NOW, GRACE))
        .isInstanceOfSatisfying(
            DomainValidationException.class,
            e -> assertThat(e.errors()).extracting("field").containsExactly("reason"));
    assertThatThrownBy(() -> Withdrawal.request(7L, "bored", "가".repeat(201), NOW, GRACE))
        .isInstanceOfSatisfying(
            DomainValidationException.class,
            e ->
                assertThat(e.errors()).extracting("field").containsExactly("reason", "reasonText"));
    assertThat(Withdrawal.request(7L, "OTHER", "가".repeat(200), NOW, GRACE).reasonText())
        .hasSize(200);
  }

  @Test
  void 취소하면_닫힌다() {
    Withdrawal w = Withdrawal.request(7L, "OTHER", null, NOW, GRACE).cancel(Closer.USER, NOW);
    assertThat(w.isOpen()).isFalse();
    assertThat(w.canceledBy()).isEqualTo(Closer.USER);
  }

  @Test
  void 회원_상태는_신청과_취소로만_탈퇴_예정을_오간다() {
    User u = new User(1L, "a@wearly.test", "민지", UserStatus.ACTIVE, UserRole.USER, NOW);
    assertThat(u.withdraw()).isTrue();
    assertThat(u.isWithdrawn()).isTrue();
    assertThat(u.withdraw()).isFalse();
    // 관리자 상태 바꾸기로는 탈퇴 예정 회원을 바꿀 수 없다
    assertThatThrownBy(() -> u.changeAccess("ACTIVE", "USER"))
        .isInstanceOf(DomainValidationException.class);
    assertThat(u.cancelWithdrawal()).isTrue();
    assertThat(u.isActive()).isTrue();
    assertThat(u.cancelWithdrawal()).isFalse();
    assertThatThrownBy(() -> u.changeAccess("WITHDRAWN", "USER"))
        .isInstanceOf(DomainValidationException.class);
  }
}
