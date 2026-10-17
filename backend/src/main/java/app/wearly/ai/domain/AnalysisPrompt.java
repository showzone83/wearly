package app.wearly.ai.domain;

import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 세 공급자가 같이 쓰는 프롬프트와 JSON 스키마. 허용 값은 활성(고를 수 있는) 코드에서 만든다 (06 문서 4장 "AI 분석"). 프롬프트에는 코드와 한국어 라벨을 함께
 * 주고, 세부 종류는 종류별로 묶는다.
 */
public final class AnalysisPrompt {

  public static final List<String> FIELDS =
      List.of(
          "name",
          "category",
          "subType",
          "color",
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
      - color: 가장 넓은 면적의 색 하나. %s
      - pattern: %s
      - seasons: 입기 좋은 계절 1~4개. %s
      - styles: 어울리는 스타일 1~2개. %s
      - formality: 1(편한 데일리), 2(비즈니스 캐주얼), 3(포멀)
      - confidence: 판단에 대한 확신 0~1

      사진이 흐리거나 옷이 아니면 가장 가까운 값을 고르고 confidence를 낮게 준다.
      """;

  private AnalysisPrompt() {}

  public static String prompt(CodeSnapshot codes) {
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
    props.put("name", Map.of("type", "string"));
    props.put("category", enumOf(codes, CodeGroups.CATEGORY));
    props.put("subType", enumOf(codes, CodeGroups.SUB_TYPE));
    props.put("color", enumOf(codes, CodeGroups.COLOR));
    props.put("pattern", enumOf(codes, CodeGroups.PATTERN));
    props.put("seasons", Map.of("type", "array", "items", enumOf(codes, CodeGroups.SEASON)));
    props.put("styles", Map.of("type", "array", "items", enumOf(codes, CodeGroups.STYLE)));
    props.put("formality", Map.of("type", "integer", "enum", List.of(1, 2, 3)));
    props.put("confidence", Map.of("type", "number"));
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", props);
    schema.put("required", FIELDS);
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> enumOf(CodeSnapshot codes, String group) {
    return Map.of(
        "type",
        "string",
        "enum",
        codes.selectableCodes(group).stream().map(CodeEntry::code).toList());
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
