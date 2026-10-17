package app.wearly.ai.adapter.out.ai.gemini;

import app.wearly.ai.adapter.out.ai.AiProviderModule;
import app.wearly.ai.adapter.out.ai.ApiKeyChecks;
import app.wearly.ai.adapter.out.ai.HttpCall;
import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.ModelListResponses;
import app.wearly.ai.adapter.out.ai.ProviderOptions;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import app.wearly.ai.domain.ModelListRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini 모듈 (generateContent, REST): 클라이언트 만들기, 키 확인(GET {baseUrl}/v1beta/models?pageSize=1), 모델
 * 목록(같은 API, generateContent가 되는 것만)
 */
@Component
public class GeminiModule implements AiProviderModule {

  /** 모델 목록 한 쪽 크기 (API 최대 1000) */
  static final int PAGE_SIZE = 1000;

  /** nextPageToken을 따라가는 최대 쪽 수 (서버가 같은 토큰을 되풀이해도 멈추게) */
  static final int MAX_PAGES = 5;

  private final JsonMapper json;
  private final JsonObjectParser parser;

  public GeminiModule(JsonMapper json) {
    this.json = json;
    this.parser = new JsonObjectParser(json);
  }

  @Override
  public AiProviderType type() {
    return AiProviderType.GEMINI;
  }

  @Override
  public AiModelPort create(AiProviderSetting s, String key, ProviderOptions o) {
    return new GeminiAiProvider(
        s.providerId(),
        s.model(),
        key,
        o.baseUrl(),
        o.timeout(),
        o.integer("maxTokens"),
        o.number("temperature"),
        o.string("schemaField"),
        json,
        parser);
  }

  @Override
  public void verify(String apiKey, ProviderOptions o) {
    ApiKeyChecks.check(
        "Gemini",
        HttpCall.get(
            HttpCall.client(o.baseUrlOr(GeminiAiProvider.DEFAULT_BASE_URL), o.timeout()),
            "/v1beta/models?pageSize=1",
            Map.of("x-goog-api-key", apiKey),
            "Gemini",
            apiKey),
        apiKey,
        json);
  }

  /**
   * GET {baseUrl}/v1beta/models?pageSize=1000 (nextPageToken이 있으면 다음 쪽). 키는 헤더 x-goog-api-key로만
   * 보낸다. name의 "models/"를 떼고, Gemini는 등록 시각을 주지 않아 createdAt은 null
   */
  @Override
  public List<AiModelEntry> listModels(String apiKey, ProviderOptions o) {
    RestClient client =
        HttpCall.client(o.baseUrlOr(GeminiAiProvider.DEFAULT_BASE_URL), o.timeout());
    Map<String, String> headers = Map.of("x-goog-api-key", apiKey);
    List<AiModelEntry> out = new ArrayList<>();
    Set<String> tokens = new HashSet<>();
    String token = null;
    for (int page = 0; page < MAX_PAGES; page++) {
      HttpCall.Result res =
          token == null
              ? HttpCall.get(
                  client, "/v1beta/models?pageSize=" + PAGE_SIZE, headers, "Gemini", apiKey)
              : HttpCall.get(
                  client,
                  "/v1beta/models?pageSize=" + PAGE_SIZE + "&pageToken={token}",
                  Map.of("token", token),
                  headers,
                  "Gemini",
                  apiKey);
      for (JsonNode m : ModelListResponses.read("Gemini", res, apiKey, json, "models")) {
        List<String> methods = new ArrayList<>();
        JsonNode supported = m.get("supportedGenerationMethods");
        if (supported != null && supported.isArray()) {
          supported.forEach(v -> methods.add(v.asString("")));
        }
        String id = ModelListRules.geminiModelId(ModelListResponses.text(m, "name"), methods);
        if (id != null) {
          out.add(AiModelEntry.live(id, ModelListResponses.text(m, "displayName"), null));
        }
      }
      token = nextToken(res);
      if (token == null || !tokens.add(token)) {
        break;
      }
    }
    return out;
  }

  private String nextToken(HttpCall.Result res) {
    JsonNode root = json.readTree(res.body());
    String next = ModelListResponses.text(root, "nextPageToken");
    return next == null || next.isBlank() ? null : next;
  }
}
