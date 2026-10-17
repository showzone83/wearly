package app.wearly.ai.adapter.out.ai.openai;

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

/** OpenAI 모듈 (Responses API, REST): 클라이언트 만들기와 키 확인(GET {baseUrl}/models) */
@Component
public class OpenAiModule implements AiProviderModule {

  private final JsonMapper json;
  private final JsonObjectParser parser;

  public OpenAiModule(JsonMapper json) {
    this.json = json;
    this.parser = new JsonObjectParser(json);
  }

  @Override
  public AiProviderType type() {
    return AiProviderType.OPENAI;
  }

  @Override
  public AiModelPort create(AiProviderSetting s, String key, ProviderOptions o) {
    return new OpenAiProvider(
        s.providerId(),
        s.model(),
        key,
        o.baseUrl(),
        o.timeout(),
        o.integer("maxTokens"),
        o.number("temperature"),
        o.string("detail"),
        json,
        parser);
  }

  @Override
  public void verify(String apiKey, ProviderOptions o) {
    ApiKeyChecks.check(
        "OpenAI",
        HttpCall.get(
            HttpCall.client(o.baseUrlOr(OpenAiProvider.DEFAULT_BASE_URL), o.timeout()),
            "/models",
            Map.of("Authorization", "Bearer " + apiKey),
            "OpenAI",
            apiKey),
        apiKey,
        json);
  }
}
