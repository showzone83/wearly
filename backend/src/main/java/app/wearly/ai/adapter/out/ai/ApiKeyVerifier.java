package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.application.port.out.ApiKeyVerifierPort;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.ProviderDefaults;
import app.wearly.common.config.WearlyProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.models.ModelListParams;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 웹에서 넣은 키를 저장하기 전에 각 사의 모델 목록 조회로 확인한다 (07 문서 6장). 엔드포인트는 각 사 공식 명세 기준: - Claude: Anthropic Java
 * SDK models().list() = GET /v1/models (x-api-key, anthropic-version) - OpenAI: GET
 * {baseUrl}/models (Authorization: Bearer) — openai/openai-openapi의 listModels - Gemini: GET
 * {baseUrl}/v1beta/models?pageSize=1 (x-goog-api-key) — generativelanguage v1beta discovery의
 * models.list 공급자 options의 timeoutSeconds·baseUrl(WEARLY_AI_ALLOW_BASE_URL=true일 때만)을 따른다. 오류 메시지에서
 * 키를 지운다.
 */
@Component
class ApiKeyVerifier implements ApiKeyVerifierPort {

  private final WearlyProperties.Ai props;
  private final JsonMapper json;

  ApiKeyVerifier(WearlyProperties properties, JsonMapper json) {
    this.props = properties.ai();
    this.json = json;
  }

  @Override
  public void verify(AiProviderSetting s, String apiKey) {
    Map<String, Object> o = s.options();
    Duration timeout =
        o.get("timeoutSeconds") instanceof Number n
            ? Duration.ofSeconds(n.longValue())
            : props.timeout();
    String baseUrl = props.allowBaseUrl() && o.get("baseUrl") instanceof String u ? u : null;
    switch (s.type()) {
      case CLAUDE -> verifyClaude(apiKey, baseUrl, timeout);
      case OPENAI -> {
        RestClient client =
            HttpCall.client(baseUrl == null ? ProviderDefaults.OPENAI_BASE_URL : baseUrl, timeout);
        check(
            "OpenAI",
            HttpCall.get(
                client, "/models", Map.of("Authorization", "Bearer " + apiKey), "OpenAI", apiKey),
            apiKey);
      }
      case GEMINI -> {
        RestClient client =
            HttpCall.client(baseUrl == null ? ProviderDefaults.GEMINI_BASE_URL : baseUrl, timeout);
        check(
            "Gemini",
            HttpCall.get(
                client,
                "/v1beta/models?pageSize=1",
                Map.of("x-goog-api-key", apiKey),
                "Gemini",
                apiKey),
            apiKey);
      }
    }
  }

  private static void verifyClaude(String apiKey, String baseUrl, Duration timeout) {
    AnthropicOkHttpClient.Builder b =
        AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).maxRetries(0);
    if (baseUrl != null) {
      b.baseUrl(baseUrl);
    }
    AnthropicClient client = b.build();
    try {
      client.models().list(ModelListParams.builder().limit(1L).build());
    } catch (AnthropicServiceException e) {
      throw new AiProviderException(
          "API_" + e.statusCode(), rejected("Claude", e.statusCode(), e.getMessage(), apiKey));
    } catch (AnthropicException e) {
      String reason = isTimeout(e) ? "TIMEOUT" : "API_CLIENT";
      throw new AiProviderException(
          reason, "Claude 호출 실패: " + Secrets.scrub(e.getMessage(), apiKey));
    } finally {
      client.close();
    }
  }

  private void check(String label, HttpCall.Result res, String apiKey) {
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
  static String rejected(String label, int status, String upstreamMessage, String apiKey) {
    if (status == 401 || status == 403) {
      return label + "가 키를 받아들이지 않았어요 (HTTP " + status + ").";
    }
    return label + " 모델 목록 조회 실패 (HTTP " + status + "): " + Secrets.scrub(upstreamMessage, apiKey);
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t.getClass().getSimpleName().contains("Timeout") || t instanceof InterruptedIOException) {
        return true;
      }
    }
    return false;
  }
}
