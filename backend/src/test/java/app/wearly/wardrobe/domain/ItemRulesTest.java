package app.wearly.wardrobe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** H2: 아이템 불변식은 도메인이 최종 보증한다 (Spring 없이). 문구·필드는 웹 요청 검증과 같다 */
class ItemRulesTest {

  static ItemInput valid() {
    return new ItemInput(
        "네이비 니트",
        "TOP",
        "KNIT",
        "navy",
        "SOLID",
        List.of("FALL"),
        List.of("NEAT"),
        2,
        "OWNED",
        null,
        null,
        null);
  }

  @Test
  void 맞는_입력은_오류가_없다() {
    assertThat(ItemRules.validate(valid(), "")).isEmpty();
  }

  @Test
  void 필수_길이_범위를_웹_검증과_같은_문구로_모아_필드_순으로_돌려준다() {
    ItemInput in =
        new ItemInput(
            " ",
            null,
            "KNIT",
            null,
            "SOLID",
            Arrays.asList("FALL", null),
            null,
            4,
            "OWNED",
            "b".repeat(41),
            -1,
            LocalDate.of(2026, 1, 1));
    assertThat(ItemRules.validate(in, "items[2]."))
        .containsExactly(
            FieldError.tooLong("items[2].brand", 40),
            FieldError.required("items[2].category"),
            FieldError.required("items[2].color"),
            FieldError.outOfRange("items[2].formality", 1, 3),
            FieldError.required("items[2].name"),
            FieldError.required("items[2].seasons[1]"),
            FieldError.required("items[2].styles"),
            FieldError.outOfRange("items[2].wornCount", 0, null));
  }

  @Test
  void 이름은_40자까지_격식은_필수() {
    ItemInput v = valid();
    ItemInput longName =
        new ItemInput(
            "가".repeat(41),
            v.category(),
            v.subType(),
            v.color(),
            v.pattern(),
            v.seasons(),
            v.styles(),
            null,
            v.status(),
            null,
            0,
            null);
    assertThat(ItemRules.validate(longName, ""))
        .containsExactly(FieldError.required("formality"), FieldError.tooLong("name", 40));
  }

  @Test
  void 도메인_모델은_불변식을_어긴_값을_받지_않는다() {
    ItemInput v = valid();
    ItemInput noName =
        new ItemInput(
            null,
            v.category(),
            v.subType(),
            v.color(),
            v.pattern(),
            v.seasons(),
            v.styles(),
            v.formality(),
            v.status(),
            null,
            null,
            null);
    // 예전에는 이름이 null이면 apply에서 NullPointerException이 났다 (점검 보고서 H2)
    assertThatThrownBy(() -> WardrobeItem.create(1L, noName))
        .isInstanceOf(DomainValidationException.class)
        .satisfies(
            e ->
                assertThat(((DomainValidationException) e).errors())
                    .containsExactly(FieldError.required("name")));
    WardrobeItem item = WardrobeItem.create(1L, v);
    assertThat(item.name()).isEqualTo("네이비 니트");
    assertThat(item.wornCount()).isZero();
  }
}
