package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.domain.AiProviderException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** REST 공급자(OpenAI·Gemini)의 키 확인 응답 해석. 실패하면 API_<상태>로 던지고, 메시지에서 키를 지운다 */
public final class ApiKeyChecks {

  private ApiKeyChecks() {}

  public static void check(String label, HttpCall.Result res, String apiKey, JsonMapper json) {
    if (res.ok()) {
      return;
    }
    String message = res.body();
    try {
      JsonNode root = res.body() == null || res.body().isBlank() ? null : json.readTree(res.body());
      if (root != null && root.isObject()) {
        message = root.path("error").path("message").asString("");
      }
    } catch (JacksonException e) {
      // 본문이 JSON이 아니면 그대로 (키는 아래에서 지운다)
    }
    throw new AiProviderException(
        "API_" + res.status(), rejected(label, res.status(), message, apiKey));
  }

  /** 인증 오류(401·403)는 키 조각이 섞일 수 있어 원문 메시지를 버린다 */
  public static String rejected(String label, int status, String upstreamMessage, String apiKey) {
    if (status == 401 || status == 403) {
      return label + "가 키를 받아들이지 않았어요 (HTTP " + status + ").";
    }
    return label + " 모델 목록 조회 실패 (HTTP " + status + "): " + Secrets.scrub(upstreamMessage, apiKey);
  }
}
