package app.wearly.ai.domain;

import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 공급자가 준 사진 분석 JSON 검증 (06 문서 4장 "AI 분석", M1). 공급자와 무관하게 같은 규칙: 활성 코드 밖 값, 종류·세부 종류 짝 불일치, 범위 밖 숫자는
 * 거부하고(INVALID_OUTPUT, 다음 공급자로), 이름은 40자로 자르고 확신도는 소수 둘째 자리로 맞춘다.
 */
public final class AiSuggestionValidator {

  static final int NAME_MAX = 40;

  private AiSuggestionValidator() {}

  /**
   * @param json 공급자 응답을 JSON 객체로 읽은 값 (String·Number·Boolean·List·Map)
   * @throws RejectedSuggestionException 규칙에 맞지 않을 때 (reason INVALID_OUTPUT)
   */
  public static AiSuggestion validate(Map<String, Object> json, CodeSnapshot codes) {
    String name = text(json, "name").strip();
    if (name.isEmpty()) {
      throw reject("name이 비었어요.");
    }
    if (name.length() > NAME_MAX) {
      name = name.substring(0, NAME_MAX).strip();
    }
    String category = one(json, "category", codes, CodeGroups.CATEGORY);
    String subType = one(json, "subType", codes, CodeGroups.SUB_TYPE);
    if (!codes.belongsTo(subType, category)) {
      throw reject("category와 subType의 짝이 맞지 않아요: " + category + "/" + subType);
    }
    final String color = one(json, "color", codes, CodeGroups.COLOR);
    final String pattern = one(json, "pattern", codes, CodeGroups.PATTERN);
    final List<String> seasons = many(json, "seasons", codes, CodeGroups.SEASON);
    final List<String> styles = many(json, "styles", codes, CodeGroups.STYLE);
    Object formality = json.get("formality");
    if (!isIntegral(formality)
        || ((Number) formality).longValue() < 1
        || ((Number) formality).longValue() > 3) {
      throw reject("formality는 1~3 정수여야 해요.");
    }
    if (!(json.get("confidence") instanceof Number c)) {
      throw reject("confidence가 없어요.");
    }
    double confidence = c.doubleValue();
    if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) {
      throw reject("confidence는 0~1이어야 해요.");
    }
    return new AiSuggestion(
        name,
        category,
        subType,
        color,
        pattern,
        seasons,
        styles,
        ((Number) formality).intValue(),
        Math.round(confidence * 100) / 100.0);
  }

  private static boolean isIntegral(Object v) {
    return v instanceof Integer
        || v instanceof Long
        || v instanceof Short
        || v instanceof java.math.BigInteger;
  }

  private static String text(Map<String, Object> json, String field) {
    if (!(json.get(field) instanceof String s)) {
      throw reject(field + "가 문자열이 아니에요.");
    }
    return s;
  }

  private static String one(
      Map<String, Object> json, String field, CodeSnapshot codes, String group) {
    String value = text(json, field);
    if (!codes.selectable(group, value)) {
      throw reject(field + " 값이 허용 목록 밖이에요: " + value);
    }
    return value;
  }

  private static List<String> many(
      Map<String, Object> json, String field, CodeSnapshot codes, String group) {
    if (!(json.get(field) instanceof List<?> list)) {
      throw reject(field + "가 배열이 아니에요.");
    }
    List<String> values = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof String value)) {
        throw reject(field + "에 문자열이 아닌 값이 있어요.");
      }
      if (!codes.selectable(group, value)) {
        throw reject(field + " 값이 허용 목록 밖이에요: " + value);
      }
      if (!values.contains(value)) {
        values.add(value);
      }
    }
    return values;
  }

  private static RejectedSuggestionException reject(String message) {
    return new RejectedSuggestionException(message);
  }
}
