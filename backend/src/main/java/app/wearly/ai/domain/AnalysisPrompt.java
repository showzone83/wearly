package app.wearly.ai.domain;

import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사진 분석 프롬프트와 JSON 스키마 (공급자와 무관, M1). 세 공급자가 같은 것을 받는다. 허용 값은 활성(고를 수 있는) 코드에서 만든다 (06 문서 4장 "AI
 * 분석"). 프롬프트에는 코드와 한국어 라벨을 함께 주고, 세부 종류는 종류별로 묶는다.
 *
 * <p>프롬프트 캐싱: 지시·허용 값 목록은 코드가 바뀔 때만 바뀌므로 고정 지시문(instructions → Claude system, OpenAI instructions,
 * Gemini systemInstruction)으로 앞에 두고, 요청마다 바뀌는 사진과 짧은 요청 문장은 사용자 메시지로 뒤에 둔다. 고정 지시문과 스키마는 같은 코드
 * 스냅샷이면 바이트 단위로 같아야 한다: 코드는 sort_order·code_id 순서, 스키마의 객체는 모두 넣은 순서를 지키는 맵이다 (Map.of는 JVM마다 순서가
 * 달라질 수 있어 두 개 이상 키에는 쓰지 않는다). 시각·회원 값 같은 바뀌는 값은 넣지 않는다.
 */
public final class AnalysisPrompt {

  public static final List<String> FIELDS =
      List.of(
          "name",
          "category",
          "subType",
          "color",
          "colors",
          "pattern",
          "seasons",
          "styles",
          "formality",
          "confidence");

  private static final String TEMPLATE =
      """
      직장인 옷장 앱에 등록할 옷 사진이다. 사진의 주된 아이템 하나를 보고 아래 값을 정해 JSON으로 답한다.

      - name: 한국어 아이템 이름, 20자 이내 (예: "네이비 울 니트")
      - category와 subType: 반드시 아래 짝 중 하나 (종류: 세부 종류들)
      %s
      - color: 가장 넓은 면적의 색 하나(주 색상). %s
      - colors: 옷에서 눈에 띄는 색 1~3개, 넓은 순서. 첫 번째는 color와 같다. 단색 옷은 color 하나만 넣는다. 허용 값은 color와 같다.
      - pattern: %s
      - seasons: 입기 좋은 계절 1~5개. 초봄·초여름·초가을은 환절기에 입기 좋은 옷에 넣는다. %s
      - styles: 어울리는 스타일 1~2개. %s
      - formality: 1(편한 데일리), 2(비즈니스 캐주얼), 3(포멀)
      - confidence: 판단에 대한 확신 0~1

      사진이 흐리거나 옷이 아니면 가장 가까운 값을 고르고 confidence를 낮게 준다.
      """;

  /** 사진과 함께 보내는 사용자 메시지 (요청마다 같지만 사진 뒤에 오므로 캐시 앞부분에 들지 않는다) */
  public static final String REQUEST = "이 사진의 주된 아이템을 위 기준으로 분석해 JSON으로 답한다.";

  /** 구조화 출력 스키마 이름 (OpenAI text.format.name) */
  public static final String SCHEMA_NAME = "wardrobe_item";

  private AnalysisPrompt() {}

  /** 사진 분석 요청: 고정 지시문(지시 + 허용 값 목록) + 사용자 메시지(사진 + REQUEST) + 허용 값 스키마. 목업 응답은 사진 해시로 만든다 */
  public static AiJsonPrompt request(AiImage image, CodeSnapshot codes) {
    return new AiJsonPrompt(
        SCHEMA_NAME,
        instructions(codes),
        REQUEST,
        schema(codes),
        () -> MockSuggestions.plan(image.sha256(), codes));
  }

  /** 고정 지시문: 지시와 허용 값 목록. 같은 코드 스냅샷이면 늘 같은 문자열이다 */
  public static String instructions(CodeSnapshot codes) {
    List<CodeEntry> categories = codes.selectableCodes(CodeGroups.CATEGORY);
    List<CodeEntry> subTypes = codes.selectableCodes(CodeGroups.SUB_TYPE);
    String pairs =
        categories.stream()
            .map(
                c ->
                    c.code()
                        + "("
                        + c.label()
                        + "): "
                        + subTypes.stream()
                            .filter(s -> c.code().equals(s.parent()))
                            .map(AnalysisPrompt::labeled)
                            .collect(Collectors.joining(", ")))
            .collect(Collectors.joining("\n"));
    return TEMPLATE.formatted(
        pairs,
        list(codes, CodeGroups.COLOR),
        list(codes, CodeGroups.PATTERN),
        list(codes, CodeGroups.SEASON),
        list(codes, CodeGroups.STYLE));
  }

  /** 허용 값을 enum으로 박은 JSON 스키마 (모든 필드 필수, 추가 필드 없음) */
  public static Map<String, Object> schema(CodeSnapshot codes) {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("name", ordered("type", "string"));
    props.put("category", enumOf(codes, CodeGroups.CATEGORY));
    props.put("subType", enumOf(codes, CodeGroups.SUB_TYPE));
    props.put("color", enumOf(codes, CodeGroups.COLOR));
    props.put("colors", ordered("type", "array", "items", enumOf(codes, CodeGroups.COLOR)));
    props.put("pattern", enumOf(codes, CodeGroups.PATTERN));
    props.put("seasons", ordered("type", "array", "items", enumOf(codes, CodeGroups.SEASON)));
    props.put("styles", ordered("type", "array", "items", enumOf(codes, CodeGroups.STYLE)));
    props.put("formality", ordered("type", "integer", "enum", List.of(1, 2, 3)));
    props.put("confidence", ordered("type", "number"));
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", props);
    schema.put("required", FIELDS);
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> enumOf(CodeSnapshot codes, String group) {
    return ordered(
        "type",
        "string",
        "enum",
        codes.selectableCodes(group).stream().map(CodeEntry::code).toList());
  }

  /** 키·값을 번갈아 받아 넣은 순서대로 둔다 (직렬화 순서 고정) */
  private static Map<String, Object> ordered(Object... keyValues) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      m.put((String) keyValues[i], keyValues[i + 1]);
    }
    return m;
  }

  private static String list(CodeSnapshot codes, String group) {
    return codes.selectableCodes(group).stream()
        .map(AnalysisPrompt::labeled)
        .collect(Collectors.joining(", "));
  }

  private static String labeled(CodeEntry c) {
    return c.code() + "(" + c.label() + ")";
  }
}
