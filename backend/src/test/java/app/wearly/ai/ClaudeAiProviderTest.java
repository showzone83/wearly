package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.ai.adapter.out.ai.JsonObjectParser;
import app.wearly.ai.adapter.out.ai.claude.ClaudeAiProvider;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 실제 키 없이 Claude 공급자를 확인한다. 로컬 가짜 HTTP 서버를 Messages API처럼 세워 SDK가 실제로 보내는 요청 본문과 응답 파싱·검증을 본다. */
class ClaudeAiProviderTest {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4};

  HttpServer server;
  final AtomicReference<String> requestBody = new AtomicReference<>();
  final AtomicReference<String> betaHeader = new AtomicReference<>();
  final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
  volatile int responseStatus = 200;
  volatile String responseBody;

  ClaudeAiProvider provider;
  final CodeSnapshot codes = TestCodes.seed();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
          apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(responseStatus, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
    AnthropicClient client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .maxRetries(0)
            .timeout(Duration.ofSeconds(10))
            .build();
    provider =
        new ClaudeAiProvider(
            "claude",
            client,
            "claude-opus-5-5",
            "medium",
            16000,
            new JsonObjectParser(JSON),
            "test-key");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void 이미지와_허용_목록을_구조화_출력_스키마로_보낸다() {
    responseBody = message(validSuggestion(), "end_turn");
    provider.analyze(new AiImage(PHOTO, "image/jpeg", "x".repeat(64)), codes);

    JsonNode req = JSON.readTree(requestBody.get());
    assertThat(apiKeyHeader.get()).isEqualTo("test-key");
    assertThat(req.get("model").asString()).isEqualTo("claude-opus-5-5");
    assertThat(req.get("max_tokens").asLong()).isEqualTo(16000);
    assertThat(req.has("thinking")).isFalse(); // Opus 5.5는 항상 적응형 사고, effort로만 조절

    JsonNode content = req.get("messages").get(0).get("content");
    assertThat(req.get("messages").get(0).get("role").asString()).isEqualTo("user");
    assertThat(content.get(0).get("type").asString()).isEqualTo("image");
    assertThat(content.get(0).get("source").get("type").asString()).isEqualTo("base64");
    assertThat(content.get(0).get("source").get("media_type").asString()).isEqualTo("image/jpeg");
    assertThat(Base64.getDecoder().decode(content.get(0).get("source").get("data").asString()))
        .isEqualTo(PHOTO);
    assertThat(content.get(1).get("type").asString()).isEqualTo("text");
    // 허용 값은 활성 코드에서: 코드와 한국어 라벨, 세부 종류는 종류별로 묶는다
    assertThat(content.get(1).get("text").asString())
        .contains(
            "TOP(상의): TEE(티셔츠), LONG_TEE(긴소매 티셔츠)",
            "DRESS(원피스): DRESS(원피스), SHIRT_DRESS(셔츠 원피스)",
            "lightblue(라이트블루)",
            "denim(데님)",
            "FLORAL(플로럴)",
            "SPORTY(스포티)");

    JsonNode output = req.get("output_config");
    assertThat(output.get("effort").asString()).isEqualTo("medium");
    assertThat(output.get("format").get("type").asString()).isEqualTo("json_schema");
    JsonNode schema = output.get("format").get("schema");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(texts(schema.get("required")))
        .containsExactlyInAnyOrder(
            "name",
            "category",
            "subType",
            "color",
            "pattern",
            "seasons",
            "styles",
            "formality",
            "confidence");
    assertThat(texts(schema.get("properties").get("category").get("enum")))
        .hasSize(7)
        .contains("DRESS");
    assertThat(texts(schema.get("properties").get("subType").get("enum")))
        .hasSize(52)
        .contains("JEANS");
    assertThat(texts(schema.get("properties").get("color").get("enum")))
        .hasSize(22)
        .contains("navy", "burgundy", "denim");
    assertThat(texts(schema.get("properties").get("seasons").get("items").get("enum")))
        .containsExactly("SPRING", "SUMMER", "FALL", "WINTER");
    assertThat(schema.get("properties").get("formality").get("enum").toString())
        .isEqualTo("[1,2,3]");

    // 거절 시 서버 측 폴백
    assertThat(req.get("fallbacks").asString()).isEqualTo("default");
    assertThat(betaHeader.get()).contains(ClaudeAiProvider.FALLBACK_BETA);
  }

  @Test
  void 응답을_검증해_제안과_토큰_수를_돌려준다() {
    responseBody = message(validSuggestion(), "end_turn");
    AiResult result = provider.analyze(new AiImage(PHOTO, "image/png", "x".repeat(64)), codes);

    assertThat(result.suggestion().name()).isEqualTo("네이비 울 니트");
    assertThat(result.suggestion().category()).isEqualTo("TOP");
    assertThat(result.suggestion().subType()).isEqualTo("KNIT");
    assertThat(result.suggestion().color()).isEqualTo("navy");
    assertThat(result.suggestion().formality()).isEqualTo(2);
    assertThat(result.suggestion().confidence()).isEqualTo(0.87);
    assertThat(result.model()).isEqualTo("claude-opus-5-5");
    assertThat(result.inputTokens()).isEqualTo(1500);
    assertThat(result.outputTokens()).isEqualTo(80);
  }

  @Test
  void 허용_목록_밖이나_짝이_틀린_값은_거부한다() {
    ObjectNode badColor = validSuggestion().put("color", "fuchsia");
    assertRejected(badColor);

    ObjectNode mismatch = validSuggestion().put("category", "SHOES");
    assertRejected(mismatch);

    ObjectNode badSeason = validSuggestion();
    badSeason.putArray("seasons").add("AUTUMN");
    assertRejected(badSeason);

    assertRejected(validSuggestion().put("formality", 5));
    assertRejected(validSuggestion().put("confidence", 1.5));
    ObjectNode missing = validSuggestion();
    missing.remove("pattern");
    assertRejected(missing);
  }

  @Test
  void 비활성_코드는_스키마에서_빠지고_응답에_오면_거부한다() {
    CodeSnapshot partial = TestCodes.withInactive("COLOR:navy", "CATEGORY:ACC");
    responseBody = message(validSuggestion(), "end_turn");
    assertThatThrownBy(() -> provider.analyze(image(), partial))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("INVALID_OUTPUT");
    JsonNode schema =
        JSON.readTree(requestBody.get()).get("output_config").get("format").get("schema");
    assertThat(texts(schema.get("properties").get("color").get("enum")))
        .hasSize(21)
        .doesNotContain("navy");
    // 종류가 비활성이면 그 하위 세부 종류(시계·벨트 …)도 고를 수 없다
    assertThat(texts(schema.get("properties").get("subType").get("enum")))
        .doesNotContain("WATCH", "BELT");
  }

  @Test
  void JSON이_아니거나_거절이면_실패() {
    responseBody = textMessage("죄송하지만 분석할 수 없어요", "end_turn");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("INVALID_OUTPUT");

    responseBody = textMessage("", "refusal");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("REFUSAL");

    responseBody = textMessage("{\"name\":", "max_tokens");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("MAX_TOKENS");
  }

  @Test
  void API_오류는_AiProviderException() {
    responseStatus = 529;
    responseBody =
        "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("API_529");

    // 인증 오류 메시지는 키 조각이 섞일 수 있어 원문을 버린다
    responseStatus = 401;
    responseBody =
        "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
            + "\"message\":\"invalid x-api-key test-key\"}}";
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .satisfies(
            e -> {
              assertThat(((AiProviderException) e).reason()).isEqualTo("API_401");
              assertThat(e.getMessage()).doesNotContain("test-key").contains("인증 실패");
            });
  }

  @Test
  void HEIC는_Claude에_보내지_않고_실패한다() {
    responseBody = message(validSuggestion(), "end_turn");
    assertThatThrownBy(
            () -> provider.analyze(new AiImage(PHOTO, "image/heic", "x".repeat(64)), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("UNSUPPORTED_MEDIA");
    assertThat(requestBody.get()).isNull();
  }

  private void assertRejected(ObjectNode suggestion) {
    responseBody = message(suggestion, "end_turn");
    assertThatThrownBy(() -> provider.analyze(image(), codes))
        .isInstanceOf(AiProviderException.class)
        .extracting("reason")
        .isEqualTo("INVALID_OUTPUT");
  }

  private static AiImage image() {
    return new AiImage(PHOTO, "image/jpeg", "x".repeat(64));
  }

  static ObjectNode validSuggestion() {
    ObjectNode n = JSON.createObjectNode();
    n.put("name", "네이비 울 니트");
    n.put("category", "TOP");
    n.put("subType", "KNIT");
    n.put("color", "navy");
    n.put("pattern", "SOLID");
    n.putArray("seasons").add("FALL").add("WINTER");
    n.putArray("styles").add("NEAT").add("CLASSIC");
    n.put("formality", 2);
    n.put("confidence", 0.873);
    return n;
  }

  private static String message(JsonNode suggestion, String stopReason) {
    return textMessage(JSON.writeValueAsString(suggestion), stopReason);
  }

  private static String textMessage(String text, String stopReason) {
    ObjectNode m = JSON.createObjectNode();
    m.put("id", "msg_test");
    m.put("type", "message");
    m.put("role", "assistant");
    m.put("model", "claude-opus-5-5");
    m.putArray("content").addObject().put("type", "text").put("text", text);
    m.put("stop_reason", stopReason);
    m.putNull("stop_sequence");
    m.putObject("usage").put("input_tokens", 1500).put("output_tokens", 80);
    return JSON.writeValueAsString(m);
  }

  private static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asString()));
    return out;
  }
}
