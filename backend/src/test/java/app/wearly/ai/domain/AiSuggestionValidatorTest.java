package app.wearly.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1: 사진 분석 결과 검증은 공급자 어댑터가 아니라 도메인에 있다 (Spring·HTTP 없이) */
class AiSuggestionValidatorTest {

  final CodeSnapshot codes = TestCodes.seed();

  static Map<String, Object> valid() {
    Map<String, Object> m = new HashMap<>();
    m.put("name", "  네이비 울 니트  ");
    m.put("category", "TOP");
    m.put("subType", "KNIT");
    m.put("color", "navy");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("FALL", "WINTER", "FALL"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("confidence", 0.876);
    return m;
  }

  @Test
  void 맞는_응답은_정리해서_제안으로() {
    AiSuggestion s = AiSuggestionValidator.validate(valid(), codes);
    assertThat(s)
        .isEqualTo(
            new AiSuggestion(
                "네이비 울 니트",
                "TOP",
                "KNIT",
                "navy",
                "SOLID",
                List.of("FALL", "WINTER"),
                List.of("NEAT"),
                2,
                0.88));
    Map<String, Object> longName = valid();
    longName.put("name", "가".repeat(45));
    assertThat(AiSuggestionValidator.validate(longName, codes).name()).hasSize(40);
  }

  @Test
  void 규칙에_맞지_않으면_INVALID_OUTPUT() {
    assertRejected(with("color", "fuchsia"));
    assertRejected(with("category", "SHOES"));
    assertRejected(with("seasons", List.of("AUTUMN")));
    assertRejected(with("seasons", "FALL"));
    assertRejected(with("formality", 2.0));
    assertRejected(with("formality", 4));
    assertRejected(with("confidence", 1.5));
    assertRejected(with("name", " "));
    assertRejected(with("pattern", null));
    CodeSnapshot partial = TestCodes.withInactive("COLOR:navy");
    assertThatThrownBy(() -> AiSuggestionValidator.validate(valid(), partial))
        .isInstanceOf(RejectedSuggestionException.class);
  }

  private static Map<String, Object> with(String key, Object value) {
    Map<String, Object> m = valid();
    m.put(key, value);
    return m;
  }

  private void assertRejected(Map<String, Object> json) {
    assertThatThrownBy(() -> AiSuggestionValidator.validate(json, codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("INVALID_OUTPUT");
  }
}
