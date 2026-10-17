package app.wearly.ai.adapter.out.ai.gemini;

import app.wearly.ai.adapter.out.ai.AiProviderModule;
import app.wearly.ai.adapter.out.ai.ApiKeyChecks;
import app.wearly.ai.adapter.out.ai.HttpCall;
import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.ProviderOptions;
import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.ai.domain.AiProviderType;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Gemini 모듈 (generateContent, REST): 클라이언트 만들기와 키 확인(GET {baseUrl}/v1beta/models?pageSize=1) */
@Component
public class GeminiModule implements AiProviderModule {

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
}
