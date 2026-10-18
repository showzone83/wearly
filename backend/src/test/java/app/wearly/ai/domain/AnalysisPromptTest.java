package app.wearly.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.OrderedMaps;
import app.wearly.support.TestCodes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 사진 분석 프롬프트의 고정 부분(캐시 앞부분)이 요청마다 바이트 단위로 같은지 본다 */
class AnalysisPromptTest {

  static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void 고정_지시문과_스키마는_사진이_달라도_스냅샷을_새로_읽어도_같다() {
    // 코드 스냅샷을 따로 두 번 읽는다 (서버 재시작·다른 인스턴스와 같은 상황)
    CodeSnapshot a = TestCodes.withInactive();
    CodeSnapshot b = TestCodes.withInactive();
    AiJsonPrompt first =
        AnalysisPrompt.request(new AiImage(new byte[] {1}, "image/jpeg", "a".repeat(64)), a);
    AiJsonPrompt second =
        AnalysisPrompt.request(new AiImage(new byte[] {2}, "image/png", "b".repeat(64)), b);

    assertThat(second.instructions()).isEqualTo(first.instructions());
    assertThat(JSON.writeValueAsString(second.schema()))
        .isEqualTo(JSON.writeValueAsString(first.schema()));
    // 요청마다 바뀌는 부분은 사진뿐이다 (사용자 메시지 문장은 고정)
    assertThat(first.prompt()).isEqualTo(AnalysisPrompt.REQUEST);
    assertThat(second.prompt()).isEqualTo(AnalysisPrompt.REQUEST);
  }

  @Test
  void 고정_지시문에는_허용_값이_코드_순서대로_들어간다() {
    String instructions = AnalysisPrompt.instructions(TestCodes.seed());
    assertThat(instructions)
        .contains("TOP(상의): TEE(티셔츠), LONG_TEE(긴소매 티셔츠)")
        .contains("- formality: 1(편한 데일리), 2(비즈니스 캐주얼), 3(포멀)")
        .doesNotContain("%s");
    assertThat(instructions.indexOf("SPRING(")).isLessThan(instructions.indexOf("WINTER("));
    // 이슈 #4: 색 1~3개(colors)와 새 계절 코드 (초봄·봄·초여름·여름·초가을·가을·겨울 순)
    assertThat(instructions)
        .contains("- colors: 옷에서 눈에 띄는 색 1~3개")
        .contains("EARLY_SPRING(초봄), SPRING(봄), EARLY_SUMMER(초여름), SUMMER(여름), EARLY_FALL(초가을)");
  }

  @Test
  void 스키마의_모든_객체는_순서가_고정된_맵이다() {
    Map<String, Object> schema = AnalysisPrompt.schema(TestCodes.seed());
    OrderedMaps.assertOrdered(schema, "schema");
    // 직렬화 순서: 객체마다 type이 먼저
    assertThat(JSON.writeValueAsString(schema.get("properties")))
        .contains("\"seasons\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[")
        .contains(
            "\"colors\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"navy\"");
    assertThat(AnalysisPrompt.FIELDS).contains("color", "colors");
    assertThat(schema.get("required")).isEqualTo(AnalysisPrompt.FIELDS);
  }

  @Test
  void 코드가_바뀌면_고정_지시문도_바뀐다() {
    assertThat(AnalysisPrompt.instructions(TestCodes.withInactive("COLOR:navy")))
        .isNotEqualTo(AnalysisPrompt.instructions(TestCodes.seed()))
        .doesNotContain("navy(");
  }
}
