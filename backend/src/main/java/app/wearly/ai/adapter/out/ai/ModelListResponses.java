package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * REST 공급자(OpenAI·Gemini)의 모델 목록 응답 읽기. HTTP 오류는 API_&lt;상태&gt;, 형식 오류는 BAD_RESPONSE. 키는 메시지에서 지운다
 */
public final class ModelListResponses {

  private ModelListResponses() {}

  /**
   * 성공 응답의 JSON 객체에서 배열 필드를 꺼낸다.
   *
   * @param arrayField 모델 배열 필드 (OpenAI data, Gemini models). Gemini는 빈 목록이면 필드를 빼고 주므로 없으면 빈 배열로 본다
   */
  public static JsonNode read(
      String label, HttpCall.Result res, String apiKey, JsonMapper json, String arrayField) {
    ApiKeyChecks.check(label, res, apiKey, json);
    JsonNode root;
    try {
      root = res.body() == null || res.body().isBlank() ? null : json.readTree(res.body());
    } catch (JacksonException e) {
      root = null;
    }
    if (root == null || !root.isObject()) {
      throw badResponse(label);
    }
    JsonNode list = root.get(arrayField);
    if (list == null || list.isNull()) {
      return json.createArrayNode();
    }
    if (!list.isArray()) {
      throw badResponse(label);
    }
    return list;
  }

  /** 문자열 필드, 없거나 문자열이 아니면 null */
  public static String text(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v != null && v.isString() ? v.asString() : null;
  }

  public static AiProviderException badResponse(String label) {
    return new AiProviderException("BAD_RESPONSE", label + " 모델 목록 응답 형식을 읽지 못했어요.");
  }
}
