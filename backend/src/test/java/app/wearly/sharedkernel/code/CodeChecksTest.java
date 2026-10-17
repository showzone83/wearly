package app.wearly.sharedkernel.code;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.support.TestCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 06 문서 4장 코드 검증 규칙 (Spring 없이) */
class CodeChecksTest {

  final CodeSnapshot codes = TestCodes.withInactive("COLOR:navy", "CATEGORY:OUTER");

  @Test
  void 모르는_값은_거부하고_새로_고르는_비활성_값도_거부한다() {
    List<FieldError> errors = new ArrayList<>();
    CodeChecks.one(codes, CodeGroups.COLOR, "color", "fuchsia", null, errors);
    CodeChecks.one(codes, CodeGroups.COLOR, "color2", "navy", null, errors);
    CodeChecks.one(codes, CodeGroups.COLOR, "color3", "white", null, errors);
    assertThat(errors)
        .containsExactly(
            new FieldError("color", CodeChecks.UNKNOWN),
            new FieldError("color2", CodeChecks.INACTIVE));
  }

  @Test
  void 저장돼_있던_값은_비활성이어도_통과하고_null은_검사하지_않는다() {
    List<FieldError> errors = new ArrayList<>();
    CodeChecks.one(codes, CodeGroups.COLOR, "color", "navy", "navy", errors);
    CodeChecks.one(codes, CodeGroups.COLOR, "color", null, null, errors);
    CodeChecks.many(
        codes, CodeGroups.COLOR, "colors", List.of("navy", "white"), List.of("navy"), errors);
    CodeChecks.many(codes, CodeGroups.COLOR, "colors", null, null, errors);
    assertThat(errors).isEmpty();
  }

  @Test
  void 배열은_원소마다_필드_이름에_번호를_붙인다() {
    List<FieldError> errors = new ArrayList<>();
    CodeChecks.many(
        codes, CodeGroups.COLOR, "colors", List.of("white", "navy", "fuchsia"), List.of(), errors);
    assertThat(errors)
        .containsExactly(
            new FieldError("colors[1]", CodeChecks.INACTIVE),
            new FieldError("colors[2]", CodeChecks.UNKNOWN));
  }

  @Test
  void 상위_종류가_비활성이면_세부_종류도_새로_고를_수_없다() {
    String coatLike =
        codes.codes(CodeGroups.SUB_TYPE).stream()
            .filter(c -> "OUTER".equals(c.parent()))
            .findFirst()
            .orElseThrow()
            .code();
    assertThat(codes.known(CodeGroups.SUB_TYPE, coatLike)).isTrue();
    assertThat(codes.selectable(CodeGroups.SUB_TYPE, coatLike)).isFalse();
    assertThat(codes.selectableCodes(CodeGroups.SUB_TYPE))
        .noneMatch(c -> "OUTER".equals(c.parent()));
    assertThat(codes.belongsTo("KNIT", "TOP")).isTrue();
    assertThat(codes.belongsTo("KNIT", "BOTTOM")).isFalse();
  }
}
