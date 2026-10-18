package app.wearly.wardrobe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 옷장 도메인 규칙: 아이템 코드 검증·저장 전 정리·통계 키 (Spring 없이) */
class WardrobeDomainTest {

  static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

  /** 예전 color 하나로 들어온 입력 (오류 필드는 color) */
  static ItemInput input(String category, String subType, String color) {
    return input(category, subType, List.of(color), true);
  }

  static ItemInput input(String category, String subType, List<String> colors, boolean legacy) {
    return new ItemInput(
        " 니트 ",
        category,
        subType,
        colors,
        legacy,
        "SOLID",
        List.of("FALL", "FALL", "WINTER"),
        List.of("NEAT"),
        2,
        "OWNED",
        "  ",
        null,
        null,
        null);
  }

  @Test
  void 종류와_세부_종류의_짝이_맞아야_한다() {
    CodeSnapshot codes = TestCodes.seed();
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(codes, input("TOP", "KNIT", "navy"), null, "", errors);
    assertThat(errors).isEmpty();
    ItemCodeValidator.validate(codes, input("BOTTOM", "KNIT", "navy"), null, "items[2].", errors);
    assertThat(errors)
        .containsExactly(new FieldError("items[2].subType", ItemCodeValidator.SUBTYPE_MISMATCH));
  }

  @Test
  void PATCH에서_안_바꾼_비활성_값은_통과한다() {
    CodeSnapshot codes = TestCodes.withInactive("COLOR:navy");
    List<FieldError> errors = new ArrayList<>();
    ItemCodeValidator.validate(
        codes, input("TOP", "KNIT", "navy"), input("TOP", "KNIT", "navy"), "", errors);
    assertThat(errors).isEmpty();
    ItemCodeValidator.validate(codes, input("TOP", "KNIT", "navy"), null, "", errors);
    assertThat(errors).extracting(FieldError::field).containsExactly("color");
  }

  @Test
  void 색_목록의_코드_검증_필드는_colors_번호이고_저장돼_있던_색은_자리가_바뀌어도_통과한다() {
    CodeSnapshot codes = TestCodes.withInactive("COLOR:navy", "COLOR:white");
    List<FieldError> errors = new ArrayList<>();
    // 보조 색이던 navy를 주 색상으로 올려도 이미 있던 색이라 통과, 새로 고른 white(비활성)는 거부
    ItemCodeValidator.validate(
        codes,
        input("TOP", "KNIT", List.of("navy", "gray", "white"), false),
        input("TOP", "KNIT", List.of("black", "navy"), false),
        "",
        errors);
    assertThat(errors).containsExactly(new FieldError("colors[2]", CodeChecks.INACTIVE));
    errors.clear();
    ItemCodeValidator.validate(
        codes, input("TOP", "KNIT", List.of("gray", "fuchsia"), false), null, "items[1].", errors);
    assertThat(errors).containsExactly(new FieldError("items[1].colors[1]", CodeChecks.UNKNOWN));
  }

  @Test
  void 새_계절_코드는_고를_수_있다() {
    CodeSnapshot codes = TestCodes.seed();
    assertThat(codes.selectableCodes(CodeGroups.SEASON))
        .extracting(c -> c.code())
        .containsExactly(
            "EARLY_SPRING", "SPRING", "EARLY_SUMMER", "SUMMER", "EARLY_FALL", "FALL", "WINTER");
    List<FieldError> errors = new ArrayList<>();
    ItemInput in =
        new ItemInput(
            "셔츠",
            "TOP",
            "SHIRT",
            List.of("white"),
            false,
            "SOLID",
            List.of("EARLY_SPRING", "EARLY_SUMMER", "EARLY_FALL"),
            List.of(),
            2,
            "OWNED",
            null,
            null,
            null,
            null);
    ItemCodeValidator.validate(codes, in, null, "", errors);
    assertThat(errors).isEmpty();
  }

  @Test
  void 저장_전_정리() {
    WardrobeItem item = WardrobeItem.create(1L, input("TOP", "KNIT", "navy"), TODAY);
    assertThat(item.name()).isEqualTo("니트");
    assertThat(item.brand()).isNull();
    assertThat(item.seasons()).containsExactly("FALL", "WINTER");
    assertThat(item.wornCount()).isZero();
    assertThat(item.toInput().formality()).isEqualTo(2);
  }

  @Test
  void 통계_키는_활성_코드_전부와_아이템이_있는_비활성_코드() {
    CodeSnapshot codes = TestCodes.withInactive("CATEGORY:OUTER", "CATEGORY:BAG");
    Map<String, Long> keyed =
        WardrobeStatsRules.keyed(
            codes, CodeGroups.CATEGORY, List.of(new KeyCount("TOP", 2), new KeyCount("OUTER", 1)));
    assertThat(keyed).containsEntry("TOP", 2L).containsEntry("OUTER", 1L).doesNotContainKey("BAG");
    assertThat(keyed).containsEntry("BOTTOM", 0L);
    assertThat(WardrobeStatsRules.utilization(1, 3)).isEqualTo(0.33);
    assertThat(WardrobeStatsRules.utilization(0, 0)).isZero();
  }
}
