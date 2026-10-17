package app.wearly.ai.adapter.out.ai.openai;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 모듈 (Responses API, REST): 클라이언트 만들기, 키 확인(GET {baseUrl}/models), 모델 목록(같은 API, 채팅·이미지 입력
 * 계열만)
 */
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

  /** GET {baseUrl}/models (한 번에 전부 온다). created(유닉스 초)를 createdAt으로, 화면 이름은 없어 ID */
  @Override
  public List<AiModelEntry> listModels(String apiKey, ProviderOptions o) {
    JsonNode data =
        ModelListResponses.read(
            "OpenAI",
            HttpCall.get(
                HttpCall.client(o.baseUrlOr(OpenAiProvider.DEFAULT_BASE_URL), o.timeout()),
                "/models",
                Map.of("Authorization", "Bearer " + apiKey),
                "OpenAI",
                apiKey),
            apiKey,
            json,
            "data");
    List<AiModelEntry> out = new ArrayList<>();
    for (JsonNode m : data) {
      String id = ModelListResponses.text(m, "id");
      if (ModelListRules.isOpenAiChatModel(id)) {
        Long created = HttpCall.longOrNull(m, "created");
        out.add(AiModelEntry.live(id, id, created == null ? null : Instant.ofEpochSecond(created)));
      }
    }
    return out;
  }
}
