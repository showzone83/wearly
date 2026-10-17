package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiSuggestion;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급자가 준 JSON을 검증해 AiSuggestion으로 바꾼다. 세 공급자가 같은 검증을 거친다. 활성 코드 밖 값, 종류·세부 종류 짝 불일치, 범위 밖 숫자는 모두
 * 거부한다.
 */
public final class SuggestionParser {

  static final int NAME_MAX = 40;

  private final JsonMapper jsonMapper;

  public SuggestionParser(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public AiSuggestion parse(String json, CodeSnapshot codes) {
    JsonNode root;
    try {
      root = jsonMapper.readTree(json);
    } catch (JacksonException e) {
      throw reject("JSON이 아니에요.");
    }
    if (root == null || !root.isObject()) {
      throw reject("JSON 객체가 아니에요.");
    }
    String name = text(root, "name").strip();
    if (name.isEmpty()) {
      throw reject("name이 비었어요.");
    }
    if (name.length() > NAME_MAX) {
      name = name.substring(0, NAME_MAX).strip();
    }
    String category = one(root, "category", codes, CodeGroups.CATEGORY);
    String subType = one(root, "subType", codes, CodeGroups.SUB_TYPE);
    if (!codes.belongsTo(subType, category)) {
      throw reject("category와 subType의 짝이 맞지 않아요: " + category + "/" + subType);
    }
    final String color = one(root, "color", codes, CodeGroups.COLOR);
    final String pattern = one(root, "pattern", codes, CodeGroups.PATTERN);
    final List<String> seasons = many(root, "seasons", codes, CodeGroups.SEASON);
    final List<String> styles = many(root, "styles", codes, CodeGroups.STYLE);
    JsonNode formalityNode = root.get("formality");
    if (formalityNode == null
        || !formalityNode.isIntegralNumber()
        || formalityNode.asInt() < 1
        || formalityNode.asInt() > 3) {
      throw reject("formality는 1~3 정수여야 해요.");
    }
    JsonNode confidenceNode = root.get("confidence");
    if (confidenceNode == null || !confidenceNode.isNumber()) {
      throw reject("confidence가 없어요.");
    }
    double confidence = confidenceNode.asDouble();
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
        formalityNode.asInt(),
        Math.round(confidence * 100) / 100.0);
  }

  private static String text(JsonNode root, String field) {
    JsonNode node = root.get(field);
    if (node == null || !node.isString()) {
      throw reject(field + "가 문자열이 아니에요.");
    }
    return node.asString();
  }

  private static String one(JsonNode root, String field, CodeSnapshot codes, String group) {
    String value = text(root, field);
    if (!codes.selectable(group, value)) {
      throw reject(field + " 값이 허용 목록 밖이에요: " + value);
    }
    return value;
  }

  private static List<String> many(JsonNode root, String field, CodeSnapshot codes, String group) {
    JsonNode node = root.get(field);
    if (node == null || !node.isArray()) {
      throw reject(field + "가 배열이 아니에요.");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isString()) {
        throw reject(field + "에 문자열이 아닌 값이 있어요.");
      }
      String value = item.asString();
      if (!codes.selectable(group, value)) {
        throw reject(field + " 값이 허용 목록 밖이에요: " + value);
      }
      if (!values.contains(value)) {
        values.add(value);
      }
    }
    return values;
  }

  private static AiProviderException reject(String message) {
    return new AiProviderException("INVALID_OUTPUT", message);
  }
}
