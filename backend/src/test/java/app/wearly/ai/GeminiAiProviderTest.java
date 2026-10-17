package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.code.CodeSnapshot;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.ProviderReplies;
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
 * 실제 키 없이 Gemini generateContent 어댑터를 확인한다.
 * 요청 형식은 Gemini API 공식 discovery 문서(GenerateContentRequest: contents.parts.inlineData,
 * generationConfig.responseMimeType·responseJsonSchema)를 따른다.
 */
class GeminiAiProviderTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final byte[] PHOTO = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
    static final String KEY = "AIzaTestGeminiSecretKey123456";

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

    GeminiAiProvider provider(String model, Double temperature) {
        return new GeminiAiProvider("gemini", model, server.baseUrl() + "/v1beta", KEY, temperature, 2048,
                new JsonHttp(JSON, Duration.ofSeconds(5), "Gemini", KEY), new SuggestionParser(JSON));
    }

    @Test
    void generateContent_요청_구성() {
        server.reply("/v1beta/models/", 200, ProviderReplies.gemini(JSON.writeValueAsString(ProviderReplies.suggestion()),
                "gemini-test-001"));
        provider("models/gemini-test", 0.4).analyze(new AiImage(PHOTO, "image/heic", "x".repeat(64)), codes);

        FakeHttpServer.Request req = server.last();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/v1beta/models/gemini-test:generateContent"); // "models/" 접두사는 한 번만
        assertThat(req.query()).isNull(); // 키를 URL에 넣지 않는다
        assertThat(req.header("x-goog-api-key")).isEqualTo(KEY);

        JsonNode body = JSON.readTree(req.body());
        JsonNode parts = body.get("contents").get(0).get("parts");
        assertThat(body.get("contents").get(0).get("role").asString()).isEqualTo("user");
        assertThat(parts.get(0).get("inlineData").get("mimeType").asString()).isEqualTo("image/heic"); // Gemini는 HEIC를 받는다
        assertThat(Base64.getDecoder().decode(parts.get(0).get("inlineData").get("data").asString())).isEqualTo(PHOTO);
        assertThat(parts.get(1).get("text").asString()).contains("DRESS(원피스): ", "denim(데님)");

        JsonNode config = body.get("generationConfig");
        assertThat(config.get("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(2048);
        assertThat(config.get("temperature").asDouble()).isEqualTo(0.4);
        JsonNode schema = config.get("responseJsonSchema");
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.has("additionalProperties")).isFalse();
        assertThat(schema.get("properties").get("color").get("enum").size()).isEqualTo(22);
        assertThat(config.has("responseSchema")).isFalse();
    }

    @Test
    void 생각_part는_건너뛰고_토큰은_출력에_생각을_더한다() {
        server.reply("/v1beta/models/", 200, ProviderReplies.gemini(JSON.writeValueAsString(ProviderReplies.suggestion()),
                "gemini-test-001"));
        AiResult r = provider("gemini-test", null).analyze(new AiImage(PHOTO, "image/png", "x".repeat(64)), codes);
        assertThat(r.suggestion().name()).isEqualTo("블루 청바지");
        assertThat(r.model()).isEqualTo("gemini-test-001");
        assertThat(r.inputTokens()).isEqualTo(900);
        assertThat(r.outputTokens()).isEqualTo(100);
        assertThat(JSON.readTree(server.last().body()).get("generationConfig").has("temperature")).isFalse();
    }

    @Test
    void 차단_잘림_오류() {
        server.reply("/v1beta/models/", 200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertReason("REFUSAL");

        ObjectNode cut = (ObjectNode) JSON.readTree(ProviderReplies.gemini("{\"name\":", "g"));
        ((ObjectNode) cut.get("candidates").get(0)).put("finishReason", "MAX_TOKENS");
        server.reply("/v1beta/models/", 200, JSON.writeValueAsString(cut));
        assertReason("MAX_TOKENS");

        server.reply("/v1beta/models/", 200, ProviderReplies.gemini("not json", "g"));
        assertReason("INVALID_OUTPUT");

        server.reply("/v1beta/models/", 400, "{\"error\":{\"code\":400,\"message\":\"API key not valid: " + KEY
                + "\",\"status\":\"INVALID_ARGUMENT\"}}");
        assertThatThrownBy(() -> provider("gemini-test", null).analyze(image(), codes))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> {
                    assertThat(((AiProviderException) e).reason()).isEqualTo("API_400");
                    assertThat(e.getMessage()).contains("API key not valid").doesNotContain(KEY).doesNotContain("AIza");
                });

        server.reply("/v1beta/models/", 500, "{\"error\":{\"code\":500,\"message\":\"internal\"}}");
        assertReason("API_500");
    }

    private void assertReason(String reason) {
        assertThatThrownBy(() -> provider("gemini-test", null).analyze(image(), codes))
                .isInstanceOf(AiProviderException.class).extracting("reason").isEqualTo(reason);
    }

    private static AiImage image() {
        return new AiImage(PHOTO, "image/jpeg", "x".repeat(64));
    }
}
