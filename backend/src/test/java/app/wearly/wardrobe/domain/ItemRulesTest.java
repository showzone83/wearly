package app.wearly.wardrobe.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** H2: 아이템 불변식은 도메인이 최종 보증한다 (Spring 없이). 문구·필드는 웹 요청 검증과 같다 */
class ItemRulesTest {

  static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  static ItemInput valid() {
    return withColors(List.of("navy"), false, null);
  }

  /** 색·구입일만 바꾼 맞는 입력 */
  static ItemInput withColors(List<String> colors, boolean legacy, LocalDate purchasedAt) {
    return new ItemInput(
        "네이비 니트",
        "TOP",
        "KNIT",
        colors,
        legacy,
        "SOLID",
        List.of("FALL"),
        List.of("NEAT"),
        2,
        "OWNED",
        null,
        null,
        null,
        purchasedAt);
  }

  @Test
  void 맞는_입력은_오류가_없다() {
    assertThat(ItemRules.validate(valid(), "", TODAY)).isEmpty();
    assertThat(
            ItemRules.validate(
                withColors(List.of("navy", "white", "gray"), false, TODAY), "", TODAY))
        .isEmpty();
  }

  @Test
  void 필수_길이_범위를_웹_검증과_같은_문구로_모아_필드_순으로_돌려준다() {
    ItemInput in =
        new ItemInput(
            " ",
            null,
            "KNIT",
            null,
            false,
            "SOLID",
            Arrays.asList("FALL", null),
            null,
            4,
            "OWNED",
            "b".repeat(41),
            -1,
            LocalDate.of(2026, 1, 1),
            null);
    assertThat(ItemRules.validate(in, "items[2].", TODAY))
        .containsExactly(
            FieldError.tooLong("items[2].brand", 40),
            FieldError.required("items[2].category"),
            FieldError.required("items[2].colors"),
            FieldError.outOfRange("items[2].formality", 1, 3),
            FieldError.required("items[2].name"),
            FieldError.required("items[2].seasons[1]"),
            FieldError.required("items[2].styles"),
            FieldError.outOfRange("items[2].wornCount", 0, null));
  }

  @Test
  void 예전_color_입력의_필수_오류는_color_필드로_알린다() {
    assertThat(ItemRules.validate(withColors(null, true, null), "", TODAY))
        .containsExactly(FieldError.required("color"));
    assertThat(ItemRules.validate(withColors(List.of(), false, null), "", TODAY))
        .containsExactly(FieldError.required("colors"));
  }

  @Test
  void 색은_3개까지_중복과_null은_안_된다() {
    assertThat(
            ItemRules.validate(
                withColors(List.of("navy", "white", "gray", "black"), false, null), "", TODAY))
        .containsExactly(FieldError.tooMany("colors", 3));
    assertThat(
            ItemRules.validate(
                withColors(List.of("navy", "white", "navy"), false, null), "", TODAY))
        .containsExactly(new FieldError("colors[2]", FieldErrorCode.DUPLICATE));
    assertThat(ItemRules.validate(withColors(Arrays.asList("navy", null), false, null), "", TODAY))
        .containsExactly(FieldError.required("colors[1]"));
  }

  @Test
  void 구입일은_1990년부터_오늘까지() {
    Map<String, Object> range = Map.of("min", "1990-01-01", "max", "2026-10-18");
    assertThat(ItemRules.validate(withColors(List.of("navy"), false, TODAY), "", TODAY)).isEmpty();
    assertThat(
            ItemRules.validate(
                withColors(List.of("navy"), false, LocalDate.of(1990, 1, 1)), "", TODAY))
        .isEmpty();
    assertThat(ItemRules.validate(withColors(List.of("navy"), false, TODAY.plusDays(1)), "", TODAY))
        .containsExactly(new FieldError("purchasedAt", FieldErrorCode.OUT_OF_RANGE, range));
    assertThat(
            ItemRules.validate(
                withColors(List.of("navy"), false, LocalDate.of(1989, 12, 31)), "items[0].", TODAY))
        .containsExactly(
            new FieldError("items[0].purchasedAt", FieldErrorCode.OUT_OF_RANGE, range));
  }

  @Test
  void 이름은_40자까지_격식은_필수() {
    ItemInput v = valid();
    ItemInput longName =
        new ItemInput(
            "가".repeat(41),
            v.category(),
            v.subType(),
            v.colors(),
            false,
            v.pattern(),
            v.seasons(),
            v.styles(),
            null,
            v.status(),
            null,
            0,
            null,
            null);
    assertThat(ItemRules.validate(longName, "", TODAY))
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
            v.colors(),
            false,
            v.pattern(),
            v.seasons(),
            v.styles(),
            v.formality(),
            v.status(),
            null,
            null,
            null,
            null);
    // 예전에는 이름이 null이면 apply에서 NullPointerException이 났다 (점검 보고서 H2)
    assertThatThrownBy(() -> WardrobeItem.create(1L, noName, TODAY))
        .isInstanceOf(DomainValidationException.class)
        .satisfies(
            e ->
                assertThat(((DomainValidationException) e).errors())
                    .containsExactly(FieldError.required("name")));
    WardrobeItem item = WardrobeItem.create(1L, v, TODAY);
    assertThat(item.name()).isEqualTo("네이비 니트");
    assertThat(item.wornCount()).isZero();
  }

  @Test
  void 첫_색이_주_색상이고_나머지는_보조_색() {
    WardrobeItem item =
        WardrobeItem.create(1L, withColors(List.of("white", "navy", "gray"), false, null), TODAY);
    assertThat(item.color()).isEqualTo("white");
    assertThat(item.subColors()).containsExactly("navy", "gray");
    assertThat(item.colors()).containsExactly("white", "navy", "gray");
    assertThat(item.toInput().colors()).containsExactly("white", "navy", "gray");
  }

  @Test
  void 구입일을_모르면_등록일을_넣고_추정_표시() {
    WardrobeItem unknown = WardrobeItem.create(1L, valid(), TODAY);
    assertThat(unknown.purchasedAt()).isEqualTo(TODAY);
    assertThat(unknown.purchasedAtEstimated()).isTrue();
    // PATCH 병합의 출발점에서 추정 구입일은 모름(null)이다
    assertThat(unknown.toInput().purchasedAt()).isNull();

    LocalDate bought = LocalDate.of(2024, 3, 2);
    WardrobeItem known = WardrobeItem.create(1L, withColors(List.of("navy"), false, bought), TODAY);
    assertThat(known.purchasedAt()).isEqualTo(bought);
    assertThat(known.purchasedAtEstimated()).isFalse();
    assertThat(known.toInput().purchasedAt()).isEqualTo(bought);
  }

  @Test
  void 저장된_아이템의_구입일을_비우면_서울_기준_등록일로() {
    // 2026-03-01 16:30 UTC = 2026-03-02 01:30 서울
    Instant created = Instant.parse("2026-03-01T16:30:00Z");
    WardrobeItem saved =
        new WardrobeItem(
            5L,
            1L,
            "니트",
            "TOP",
            "KNIT",
            "navy",
            List.of(),
            "SOLID",
            List.of(),
            List.of(),
            2,
            "OWNED",
            null,
            0,
            null,
            LocalDate.of(2025, 1, 1),
            false,
            created,
            null);
    saved.apply(withColors(List.of("navy"), false, null), TODAY);
    assertThat(saved.purchasedAt()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(saved.purchasedAtEstimated()).isTrue();
  }
}
