package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.gemini.GeminiAiProvider;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AnalysisPrompt;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.TestCodes;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실제 키 없이 Gemini 공급자(generateContent)를 확인한다. 형식 근거: Gemini API 공식 discovery 문서(v1beta)
 * GenerateContentRequest / GenerateContentResponse / UsageMetadata.
 */
class GeminiAiProviderTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final byte[] PHOTO = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
  static final String KEY = "AIzaTestGeminiKey0123456789";

  FakeHttpServer server;
  final CodeSnapshot codes = TestCodes.seed();

  @BeforeEach
  void setUp() {
    server = new FakeHttpServer();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  GeminiAiProvider provider(String model, String schemaField) {
    return new GeminiAiProvider(
        "gemini",
        model,
        KEY,
        server.url(),
        Duration.ofSeconds(5),
        null,
        null,
        schemaField,
        JSON,
        new JsonObjectParser(JSON));
  }

  @Test
  void 요청은_inlineData와_responseFormat_JSON_스키마이고_키는_헤더로만_보낸다() {
    server.respond(200, response(ClaudeAiProviderTest.validSuggestion(), "STOP"));
    provider("gemini-test", null).analyze(new AiImage(PHOTO, "image/png", "x".repeat(64)), codes);

    FakeHttpServer.Request req = server.last();
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
    assertThat(req.query()).isNull(); // key= 쿼리를 쓰지 않는다
    assertThat(req.header("x-goog-api-key")).isEqualTo(KEY);

    JsonNode body = JSON.readTree(req.body());
    JsonNode parts = body.get("contents").get(0).get("parts");
    assertThat(body.get("contents").get(0).get("role").asString()).isEqualTo("user");
    assertThat(parts.get(0).get("inlineData").get("mimeType").asString()).isEqualTo("image/png");
    assertThat(Base64.getDecoder().decode(parts.get(0).get("inlineData").get("data").asString()))
        .isEqualTo(PHOTO);
    assertThat(parts.get(1).get("text").asString()).contains("BOTTOM(하의): SLACKS(슬랙스)");

    JsonNode config = body.get("generationConfig");
    assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(8192);
    assertThat(config.has("temperature")).isFalse();
    JsonNode text = config.get("responseFormat").get("text");
    assertThat(text.get("mimeType").asString()).isEqualTo("APPLICATION_JSON");
    assertThat(OpenAiProviderTest.texts(text.get("schema").get("required")))
        .containsExactlyElementsOf(AnalysisPrompt.FIELDS);
    assertThat(
            OpenAiProviderTest.texts(text.get("schema").get("properties").get("color").get("enum")))
        .hasSize(22);
    assertThat(config.has("responseJsonSchema")).isFalse();
  }

  @Test
  void schemaField_옵션이면_responseMimeType과_responseJsonSchema로_보낸다() {
    server.respond(200, response(ClaudeAiProviderTest.validSuggestion(), "STOP"));
    provider("models/gemini-test", GeminiAiProvider.SCHEMA_RESPONSE_JSON_SCHEMA)
        .analyze(image(), codes);
    assertThat(server.last().path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
    JsonNode config = JSON.readTree(server.last().body()).get("generationConfig");
    assertThat(config.get("responseMimeType").asString()).isEqualTo("application/json");
    assertThat(config.get("responseJsonSchema").get("type").asString()).isEqualTo("object");
    assertThat(config.has("responseFormat")).isFalse();
  }

  @Test
  void 응답_텍스트를_검증하고_생각_파트는_빼고_토큰을_더한다() {
    ObjectNode r =
        JSON.readValue(response(ClaudeAiProviderTest.validSuggestion(), "STOP"), ObjectNode.class);
    // 생각 파트(thought=true)는 결과 텍스트에 넣지 않는다
    ((tools.jackson.databind.node.ArrayNode) r.get("candidates").get(0).get("content").get("parts"))
        .insertObject(0)
        .put("text", "생각 요약")
        .put("thought", true);
    server.respond(200, JSON.writeValueAsString(r));
    AiResult result = provider("gemini-test", null).analyze(image(), codes);
    assertThat(result.suggestion().name()).isEqualTo("네이비 울 니트");
    assertThat(result.model()).isEqualTo("gemini-test-001");
    assertThat(result.inputTokens()).isEqualTo(900);
    assertThat(result.outputTokens()).isEqualTo(70 + 30); // candidates + thoughts
  }

  @Test
  void 차단_잘림_오류() {
    ObjectNode blocked = JSON.createObjectNode();
    blocked.putObject("promptFeedback").put("blockReason", "SAFETY");
    server.respond(200, JSON.writeValueAsString(blocked));
    assertReason("REFUSAL");

    server.respond(200, response(ClaudeAiProviderTest.validSuggestion(), "SAFETY"));
    assertReason("REFUSAL");

    server.respond(200, response(ClaudeAiProviderTest.validSuggestion(), "MAX_TOKENS"));
    assertReason("MAX_TOKENS");

    server.respond(200, "{\"candidates\":[]}");
    assertReason("EMPTY_OUTPUT");

    server.respond(
        200, response(ClaudeAiProviderTest.validSuggestion().put("pattern", "PAISLEY"), "STOP"));
    assertReason("INVALID_OUTPUT");

    server.respond(
        503,
        "{\"error\":{\"code\":503,\"message\":\"The model is overloaded.\","
            + "\"status\":\"UNAVAILABLE\"}}");
    assertThatThrownBy(() -> provider("gemini-test", null).analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            e -> {
              assertThat(((AiProviderException) e).reason()).isEqualTo("API_503");
              assertThat(e.getMessage()).contains("overloaded");
            });

    server.respond(
        400, "{\"error\":{\"code\":400,\"message\":\"API key not valid: " + KEY + "\"}}");
    assertThatThrownBy(() -> provider("gemini-test", null).analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain("AIza"));
  }

  private void assertReason(String reason) {
    assertThatThrownBy(() -> provider("gemini-test", null).analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo(reason);
  }

  private static AiImage image() {
    return new AiImage(PHOTO, "image/png", "x".repeat(64));
  }

  static String response(JsonNode suggestion, String finishReason) {
    ObjectNode r = JSON.createObjectNode();
    ObjectNode candidate = r.putArray("candidates").addObject();
    candidate
        .putObject("content")
        .put("role", "model")
        .putArray("parts")
        .addObject()
        .put("text", JSON.writeValueAsString(suggestion));
    candidate.put("finishReason", finishReason);
    candidate.put("index", 0);
    r.putObject("usageMetadata")
        .put("promptTokenCount", 900)
        .put("candidatesTokenCount", 70)
        .put("thoughtsTokenCount", 30)
        .put("totalTokenCount", 1000);
    r.put("modelVersion", "gemini-test-001");
    return JSON.writeValueAsString(r);
  }
}
