package app.wearly.wardrobe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 사용 중지·다시 사용 규칙 (13 문서 2·3장, Spring 없이) */
class RetirementTest {

  static final Instant T1 = Instant.parse("2026-10-17T01:00:00Z");
  static final Instant T2 = Instant.parse("2026-10-18T01:00:00Z");

  static WardrobeItem item() {
    return WardrobeItem.create(
        7L, WardrobeDomainTest.input("TOP", "KNIT", "navy"), WardrobeDomainTest.TODAY);
  }

  @Test
  void 사용_중지하면_시각_이유_메모가_남고_보유_상태는_그대로다() {
    WardrobeItem it = item();
    assertThat(it.isRetired()).isFalse();

    it.retire("DONATED", "  아름다운가게 기부 ", T1);

    assertThat(it.isRetired()).isTrue();
    assertThat(it.retirement()).isEqualTo(new Retirement(T1, "DONATED", "아름다운가게 기부"));
    assertThat(it.status()).isEqualTo("OWNED");
  }

  @Test
  void 이미_사용_중지면_이유와_메모만_바꾸고_시각은_그대로다() {
    WardrobeItem it = item();
    it.retire("DONATED", "기부", T1);

    it.retire("SOLD", "  ", T2);

    assertThat(it.retirement()).isEqualTo(new Retirement(T1, "SOLD", null));
  }

  @Test
  void 다시_사용하면_사용_중지_값이_모두_비고_원래_상태로_돌아온다() {
    WardrobeItem it = item();
    it.retire("NOT_WORN", null, T1);

    it.restore();

    assertThat(it.isRetired()).isFalse();
    assertThat(it.retirement()).isNull();
    assertThat(it.status()).isEqualTo("OWNED");
    // 사용 중에 다시 사용해도 그대로
    it.restore();
    assertThat(it.isRetired()).isFalse();
  }

  @Test
  void 이유는_필수이고_메모는_200자까지다() {
    assertThat(RetireRules.validate(null, "x".repeat(201)))
        .containsExactly(
            new FieldError("reason", RetireRules.REQUIRED),
            FieldError.tooLong("memo", RetireRules.MEMO_MAX));
    // 앞뒤 공백을 지운 뒤 센다, 이모지는 한 글자
    assertThat(RetireRules.validate("ETC", "  " + "😀".repeat(200) + "  ")).isEmpty();
    assertThatThrownBy(() -> item().retire(" ", null, T1))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void 이유는_활성_RETIRE_REASON_코드여야_한다() {
    CodeSnapshot codes = TestCodes.withInactive("RETIRE_REASON:SOLD");

    assertThat(RetireRules.validate(codes, "DONATED", null)).isEmpty();
    assertThat(RetireRules.validate(codes, "LOST", null))
        .containsExactly(new FieldError("reason", CodeChecks.UNKNOWN));
    assertThat(RetireRules.validate(codes, "SOLD", null))
        .containsExactly(new FieldError("reason", CodeChecks.INACTIVE));
    // 다른 그룹의 코드도 모르는 값
    assertThat(RetireRules.validate(codes, "OWNED", null))
        .containsExactly(new FieldError("reason", CodeChecks.UNKNOWN));
  }
}
