package app.wearly.code.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 코드 입력 규칙: attrs 검증·정규화, 코드 형식, 순서 (07 문서 4장, Spring 없이) */
class CodeAttrsTest {

  final CodeSnapshot codes = TestCodes.seed();

  @Test
  void COLOR_hex는_대문자로_pairs는_중복_제거와_정렬() {
    List<FieldError> errors = new ArrayList<>();
    Map<String, Object> out =
        CodeAttrs.validate(
            "COLOR",
            Map.of(
                "hex",
                "#abcdef",
                "neutral",
                false,
                "light",
                0.5,
                "pairs",
                List.of("white", "navy", "white")),
            "zz",
            codes,
            errors);
    assertThat(errors).isEmpty();
    assertThat(out)
        .containsEntry("hex", "#ABCDEF")
        .containsEntry("pairs", List.of("navy", "white"));
    assertThat(out.keySet()).containsExactly("hex", "neutral", "light", "pairs");
  }

  @Test
  void 틀린_attrs는_attrs_키_필드_오류() {
    List<FieldError> errors = new ArrayList<>();
    CodeAttrs.validate(
        "COLOR",
        Map.of(
            "hex", "red", "neutral", "no", "light", 2, "pairs", List.of("zz", "nope"), "extra", 1),
        "zz",
        codes,
        errors);
    assertThat(errors)
        .extracting(FieldError::field)
        .contains("attrs.extra", "attrs.hex", "attrs.neutral", "attrs.light", "attrs.pairs");

    List<FieldError> sub = new ArrayList<>();
    CodeAttrs.validate(
        "SUB_TYPE", Map.of("volume", 2, "warmth", 1.5, "shape", ""), "X", codes, sub);
    assertThat(sub)
        .extracting(FieldError::field)
        .containsExactlyInAnyOrder("attrs.volume", "attrs.warmth", "attrs.shape");

    List<FieldError> pattern = new ArrayList<>();
    CodeAttrs.validate("PATTERN", Map.of("slot", "TOP"), "X", codes, pattern);
    assertThat(pattern).extracting(FieldError::field).containsExactly("attrs.slot");
  }

  @Test
  void 코드_형식과_parent와_순서() {
    List<FieldError> errors = new ArrayList<>();
    assertThat(CodeRules.code("COLOR", " sky_blue ", errors)).isEqualTo("sky_blue");
    assertThat(CodeRules.code("PATTERN", "dots", errors)).isEqualTo("dots");
    assertThat(CodeRules.parent("SUB_TYPE", null, codes, errors)).isNull();
    assertThat(CodeRules.parent("COLOR", "TOP", codes, errors)).isEqualTo("TOP");
    assertThat(CodeRules.parent("SUB_TYPE", "TOP", codes, errors)).isEqualTo("TOP");
    assertThat(errors).extracting(FieldError::field).containsExactly("code", "parent", "parent");

    assertThat(CodeRules.isCompleteOrder(List.of("A", "B"), List.of("B", "A"))).isTrue();
    assertThat(CodeRules.isCompleteOrder(List.of("A", "B"), List.of("A", "A"))).isFalse();
    assertThat(CodeRules.isCompleteOrder(List.of("A", "B"), List.of("A"))).isFalse();
    assertThat(CodeRules.isCompleteOrder(List.of("A", "B"), null)).isFalse();
  }

  @Test
  void BODY_TYPE은_설명과_한_줄_특징이_필요하고_공백을_정리한다() {
    List<FieldError> errors = new ArrayList<>();
    Map<String, Object> out =
        CodeAttrs.validate(
            "BODY_TYPE",
            Map.of("description", "  탄탄한 몸 ", "hint", "허리 위치가 높아요"),
            "X",
            codes,
            errors);
    assertThat(errors).isEmpty();
    assertThat(out).containsEntry("description", "탄탄한 몸").containsEntry("hint", "허리 위치가 높아요");
    assertThat(out.keySet()).containsExactly("description", "hint");

    List<FieldError> bad = new ArrayList<>();
    CodeAttrs.validate(
        "BODY_TYPE",
        Map.of("description", " ", "hint", "가".repeat(CodeAttrs.TEXT_MAX + 1), "volume", 1),
        "X",
        codes,
        bad);
    assertThat(bad)
        .extracting(e -> e.field() + "=" + e.code())
        .containsExactlyInAnyOrder(
            "attrs.volume=NOT_ALLOWED", "attrs.description=REQUIRED", "attrs.hint=TOO_LONG");

    List<FieldError> missing = new ArrayList<>();
    CodeAttrs.validate("BODY_TYPE", Map.of("hint", 3), "X", codes, missing);
    assertThat(missing)
        .extracting(e -> e.field() + "=" + e.code())
        .containsExactlyInAnyOrder("attrs.description=REQUIRED", "attrs.hint=INVALID_TYPE");
  }

  @Test
  void AGE_RANGE와_FIT은_attrs가_없다() {
    List<FieldError> errors = new ArrayList<>();
    assertThat(CodeAttrs.validate("AGE_RANGE", Map.of(), "X", codes, errors)).isEmpty();
    assertThat(CodeAttrs.validate("FIT", Map.of("volume", -1), "X", codes, errors)).isEmpty();
    assertThat(errors).extracting(FieldError::field).containsExactly("attrs.volume");
  }

  @Test
  void 초기값의_체형_코드는_모두_attrs_검증을_통과한다() {
    assertThat(codes.codes("BODY_TYPE"))
        .extracting(c -> c.code())
        .containsExactly("STRAIGHT", "WAVE", "NATURAL", "UNKNOWN");
    codes
        .codes("BODY_TYPE")
        .forEach(
            c -> {
              List<FieldError> errors = new ArrayList<>();
              CodeAttrs.validate("BODY_TYPE", c.attrs(), c.code(), codes, errors);
              assertThat(errors).as(c.code()).isEmpty();
            });
  }
}
