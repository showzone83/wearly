package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.openai.OpenAiProvider;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AnalysisPrompt;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.TestCodes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실제 키 없이 OpenAI 공급자(Responses API)를 확인한다. 로컬 가짜 서버로 요청 JSON 구성·응답 파싱·오류 처리를 본다. 형식 근거: OpenAI 공식
 * OpenAPI 명세의 CreateResponse / Response / ResponseUsage.
 */
class OpenAiProviderTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
  static final String KEY = "sk-test-openai-key-123456";

  FakeHttpServer server;
  OpenAiProvider provider;
  final CodeSnapshot codes = TestCodes.seed();

  @BeforeEach
  void setUp() {
    server = new FakeHttpServer();
    provider =
        new OpenAiProvider(
            "openai",
            "gpt-test",
            KEY,
            server.url() + "/v1",
            Duration.ofSeconds(5),
            null,
            0.2,
            null,
            JSON,
            new JsonObjectParser(JSON));
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void 요청은_input_image_data_URL과_json_schema_strict_구조화_출력이다() {
    server.respond(200, response(ClaudeAiProviderTest.validSuggestion()));
    provider.analyze(new AiImage(PHOTO, "image/jpeg", "x".repeat(64)), codes);

    FakeHttpServer.Request req = server.last();
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.path()).isEqualTo("/v1/responses");
    assertThat(req.header("Authorization")).isEqualTo("Bearer " + KEY);
    assertThat(req.header("Content-Type")).startsWith("application/json");

    JsonNode body = JSON.readTree(req.body());
    assertThat(body.get("model").asString()).isEqualTo("gpt-test");
    assertThat(body.get("max_output_tokens").asInt()).isEqualTo(16000);
    assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
    assertThat(body.get("store").asBoolean()).isFalse();
    JsonNode message = body.get("input").get(0);
    assertThat(message.get("role").asString()).isEqualTo("user");
    JsonNode content = message.get("content");
    assertThat(content.get(0).get("type").asString()).isEqualTo("input_text");
    assertThat(content.get(0).get("text").asString()).contains("TOP(상의): TEE(티셔츠)");
    assertThat(content.get(1).get("type").asString()).isEqualTo("input_image");
    assertThat(content.get(1).get("detail").asString()).isEqualTo("auto");
    String url = content.get(1).get("image_url").asString();
    assertThat(url).startsWith("data:image/jpeg;base64,");
    assertThat(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))).isEqualTo(PHOTO);

    JsonNode format = body.get("text").get("format");
    assertThat(format.get("type").asString()).isEqualTo("json_schema");
    assertThat(format.get("name").asString()).isEqualTo("wardrobe_item");
    assertThat(format.get("strict").asBoolean()).isTrue();
    JsonNode schema = format.get("schema");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(texts(schema.get("required"))).containsExactlyElementsOf(AnalysisPrompt.FIELDS);
    assertThat(texts(schema.get("properties").get("subType").get("enum"))).hasSize(52);
  }

  @Test
  void 응답의_output_text를_검증하고_토큰_수를_읽는다() {
    server.respond(200, response(ClaudeAiProviderTest.validSuggestion()));
    AiResult r = provider.analyze(image(), codes);
    assertThat(r.suggestion().subType()).isEqualTo("KNIT");
    assertThat(r.suggestion().confidence()).isEqualTo(0.87);
    assertThat(r.model()).isEqualTo("gpt-test-2026-01-01");
    assertThat(r.inputTokens()).isEqualTo(1200);
    assertThat(r.outputTokens()).isEqualTo(345);
  }

  @Test
  void 거절_잘림_허용_목록_밖은_실패() {
    ObjectNode refusal = base("completed");
    refusal
        .putArray("output")
        .addObject()
        .put("type", "message")
        .put("role", "assistant")
        .putArray("content")
        .addObject()
        .put("type", "refusal")
        .put("refusal", "할 수 없어요");
    server.respond(200, JSON.writeValueAsString(refusal));
    assertReason("REFUSAL");

    ObjectNode incomplete = base("incomplete");
    incomplete.putObject("incomplete_details").put("reason", "max_output_tokens");
    incomplete.putArray("output");
    server.respond(200, JSON.writeValueAsString(incomplete));
    assertReason("MAX_TOKENS");

    server.respond(200, response(ClaudeAiProviderTest.validSuggestion().put("subType", "COAT")));
    assertReason("INVALID_OUTPUT");

    ObjectNode empty = base("completed");
    empty.putArray("output").addObject().put("type", "reasoning");
    server.respond(200, JSON.writeValueAsString(empty));
    assertReason("EMPTY_OUTPUT");
  }

  @Test
  void HTTP_오류는_상태별_이유이고_키는_메시지에_남지_않는다() {
    server.respond(500, "{\"error\":{\"message\":\"server exploded\",\"type\":\"server_error\"}}");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            e -> {
              assertThat(((AiProviderException) e).reason()).isEqualTo("API_500");
              assertThat(e.getMessage()).contains("server exploded");
            });

    server.respond(
        401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-test-op*****3456\"}}");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            e -> {
              assertThat(((AiProviderException) e).reason()).isEqualTo("API_401");
              assertThat(e.getMessage()).doesNotContain("sk-").doesNotContain("3456");
            });

    server.respond(400, "{\"error\":{\"message\":\"bad key " + KEY + " here\"}}");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain("sk-test"));

    server.respond(502, "<html>bad gateway</html>");
    assertReason("API_502");
  }

  @Test
  void 시간_초과는_TIMEOUT() {
    OpenAiProvider slow =
        new OpenAiProvider(
            "openai",
            "gpt-test",
            KEY,
            server.url(),
            Duration.ofMillis(300),
            100,
            null,
            "low",
            JSON,
            new JsonObjectParser(JSON));
    server.delay(1500).respond(200, response(ClaudeAiProviderTest.validSuggestion()));
    assertThatThrownBy(() -> slow.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("TIMEOUT");
  }

  private void assertReason(String reason) {
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo(reason);
  }

  private static AiImage image() {
    return new AiImage(PHOTO, "image/jpeg", "x".repeat(64));
  }

  static ObjectNode base(String status) {
    ObjectNode r = JSON.createObjectNode();
    r.put("id", "resp_test");
    r.put("object", "response");
    r.put("status", status);
    r.put("model", "gpt-test-2026-01-01");
    r.putNull("error");
    r.putObject("usage")
        .put("input_tokens", 1200)
        .put("output_tokens", 345)
        .put("total_tokens", 1545);
    return r;
  }

  /** Response 객체: output = [reasoning, message{content=[output_text]}] */
  static String response(JsonNode suggestion) {
    ObjectNode r = base("completed");
    var output = r.putArray("output");
    output.addObject().put("type", "reasoning").put("id", "rs_1");
    output
        .addObject()
        .put("type", "message")
        .put("id", "msg_1")
        .put("role", "assistant")
        .put("status", "completed")
        .putArray("content")
        .addObject()
        .put("type", "output_text")
        .put("text", JSON.writeValueAsString(suggestion))
        .putArray("annotations");
    return JSON.writeValueAsString(r);
  }

  static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asString()));
    return out;
  }
}
