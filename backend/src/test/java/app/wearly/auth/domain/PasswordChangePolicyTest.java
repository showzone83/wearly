package app.wearly.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 비밀번호 변경 기한 계산 (28 문서) */
class PasswordChangePolicyTest {

  static final Instant CHANGED = Instant.parse("2026-07-01T00:00:00Z");
  final PasswordChangePolicy policy = new PasswordChangePolicy(Duration.ofDays(90), 500);

  @Test
  void 기한은_정한_시각부터_주기만큼_뒤이고_그_시각부터_지난_것으로_본다() {
    Instant due = CHANGED.plus(Duration.ofDays(90));

    assertThat(policy.dueAt(CHANGED, null)).isEqualTo(due);
    assertThat(policy.isDue(CHANGED, null, due.minusSeconds(1))).isFalse();
    assertThat(policy.isDue(CHANGED, null, due)).isTrue();
    assertThat(policy.intervalDays()).isEqualTo(90);
  }

  @Test
  void 연장하면_연장한_시각부터_다시_센다() {
    Instant postponed = CHANGED.plus(Duration.ofDays(95));

    assertThat(policy.dueAt(CHANGED, postponed)).isEqualTo(postponed.plus(Duration.ofDays(90)));
    assertThat(policy.isDue(CHANGED, postponed, postponed.plus(Duration.ofDays(1)))).isFalse();
    // 연장 뒤에 비밀번호를 바꾸면 늦은 쪽(변경)이 기준이다
    assertThat(policy.dueAt(postponed.plusSeconds(60), postponed))
        .isEqualTo(postponed.plusSeconds(60).plus(Duration.ofDays(90)));
  }

  @Test
  void 주기가_0이면_기한이_없다() {
    PasswordChangePolicy off = new PasswordChangePolicy(Duration.ZERO, 500);

    assertThat(off.enabled()).isFalse();
    assertThat(off.dueAt(CHANGED, null)).isNull();
    assertThat(off.isDue(CHANGED, null, CHANGED.plus(Duration.ofDays(9999)))).isFalse();
    assertThat(off.intervalDays()).isZero();
    assertThat(policy.dueAt(null, null)).isNull();
  }

  @Test
  void 음수_주기와_0개_묶음은_서버가_시작하지_않는다() {
    assertThatThrownBy(() -> new PasswordChangePolicy(Duration.ofDays(-1), 500))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PasswordChangePolicy(Duration.ofDays(90), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
