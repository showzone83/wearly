package app.wearly.ai;

import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 세 공급자가 함께 쓰는 프롬프트와 JSON 스키마. 허용 값은 활성 코드에서 만든다 (doc/06_코드_관리.md 4장).
 * 세부 종류는 상위 종류별로 묶어 보여 주고, 코드와 한국어 라벨을 함께 준다.
 */
public final class AiPrompt {

    public static final List<String> REQUIRED = List.of("name", "category", "subType", "color", "pattern", "seasons",
            "styles", "formality", "confidence");

    private AiPrompt() {
    }

    public static String prompt(CodeSnapshot codes) {
        String pairs = codes.selectableCodes(CodeGroups.CATEGORY).stream()
                .map(c -> {
                    String subs = codes.selectableCodes(CodeGroups.SUB_TYPE).stream()
                            .filter(s -> c.code().equals(s.parent()))
                            .map(AiPrompt::codeLabel)
                            .collect(Collectors.joining(", "));
                    return subs.isEmpty() ? null : codeLabel(c) + ": " + subs;
                })
                .filter(line -> line != null)
                .collect(Collectors.joining("\n"));
        return """
                직장인 옷장 앱에 등록할 옷 사진이다. 사진의 주된 아이템 하나를 보고 아래 값을 정해 JSON으로 답한다.
                값은 괄호 앞의 코드를 그대로 쓴다.

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
                """.formatted(pairs, list(codes, CodeGroups.COLOR), list(codes, CodeGroups.PATTERN),
                list(codes, CodeGroups.SEASON), list(codes, CodeGroups.STYLE));
    }

    /**
     * 허용 값을 enum으로 박은 JSON 스키마.
     *
     * @param closed additionalProperties:false를 넣을지 (Claude·OpenAI strict는 필요, Gemini는 넣지 않는다)
     */
    public static Map<String, Object> schema(CodeSnapshot codes, boolean closed) {
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
        schema.put("required", REQUIRED);
        if (closed) {
            schema.put("additionalProperties", false);
        }
        return schema;
    }

    private static Map<String, Object> enumOf(CodeSnapshot codes, String group) {
        List<String> values = codes.selectableCodes(group).stream().map(CodeSnapshot.Entry::code).toList();
        return Map.of("type", "string", "enum", values);
    }

    private static String list(CodeSnapshot codes, String group) {
        return codes.selectableCodes(group).stream().map(AiPrompt::codeLabel).collect(Collectors.joining(", "));
    }

    private static String codeLabel(CodeSnapshot.Entry e) {
        return e.code() + "(" + e.label() + ")";
    }
}
