package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderException;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급자가 준 구조화 출력 텍스트를 JSON 객체(Map·List·값)로 읽는다. 세 공급자가 같이 쓴다. 내용 검증(사진 분석의 코드 검증, 코디 후보 검증)은 공급자 밖에서
 * 한다 (M1).
 */
public final class JsonObjectParser {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JsonMapper jsonMapper;

  public JsonObjectParser(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /** JSON 객체가 아니면 INVALID_OUTPUT */
  public Map<String, Object> parseObject(String json) {
    JsonNode root;
    try {
      root = jsonMapper.readTree(json);
    } catch (JacksonException e) {
      throw reject("JSON이 아니에요.");
    }
    if (root == null || !root.isObject()) {
      throw reject("JSON 객체가 아니에요.");
    }
    return jsonMapper.convertValue(root, MAP);
  }

  private static AiProviderException reject(String message) {
    return new AiProviderException("INVALID_OUTPUT", message);
  }
}
