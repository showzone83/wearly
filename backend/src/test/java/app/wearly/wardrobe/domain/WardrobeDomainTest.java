package app.wearly.wardrobe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 옷장 도메인 규칙: 아이템 코드 검증·저장 전 정리·통계 키 (Spring 없이) */
class WardrobeDomainTest {

  static ItemInput input(String category, String subType, String color) {
    return new ItemInput(
        " 니트 ",
        category,
        subType,
        color,
        "SOLID",
        List.of("FALL", "FALL", "WINTER"),
        List.of("NEAT"),
        2,
        "OWNED",
        "  ",
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
  void 저장_전_정리() {
    WardrobeItem item = WardrobeItem.create(1L, input("TOP", "KNIT", "navy"));
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
