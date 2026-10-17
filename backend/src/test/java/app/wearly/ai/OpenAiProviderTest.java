package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.code.CodeSnapshot;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.ProviderReplies;
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
 * 실제 키 없이 OpenAI Responses API 어댑터를 확인한다.
 * 요청 형식은 OpenAI 공식 OpenAPI 명세(CreateResponse: input_text·input_image, text.format json_schema strict)를 따른다.
 */
class OpenAiProviderTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    static final String KEY = "sk-test-openai-secret-123456";

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

    OpenAiProvider provider(Double temperature, Duration timeout) {
        return new OpenAiProvider("openai", "gpt-test", server.baseUrl() + "/v1", KEY, temperature, 4000,
                new JsonHttp(JSON, timeout, "OpenAI", KEY), new SuggestionParser(JSON));
    }

    @Test
    void Responses_API_요청_구성() {
        server.reply("/v1/responses", 200, ProviderReplies.openai(JSON.writeValueAsString(ProviderReplies.suggestion()),
                "gpt-test-2026"));
        provider(0.2, Duration.ofSeconds(5)).analyze(new AiImage(PHOTO, "image/jpeg", "x".repeat(64)), codes);

        FakeHttpServer.Request req = server.last();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/v1/responses");
        assertThat(req.header("Authorization")).isEqualTo("Bearer " + KEY);
        assertThat(req.header("Content-Type")).startsWith("application/json");

        JsonNode body = JSON.readTree(req.body());
        assertThat(body.get("model").asString()).isEqualTo("gpt-test");
        assertThat(body.get("max_output_tokens").asInt()).isEqualTo(4000);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.get("store").asBoolean()).isFalse();
        JsonNode msg = body.get("input").get(0);
        assertThat(msg.get("role").asString()).isEqualTo("user");
        assertThat(msg.get("content").get(0).get("type").asString()).isEqualTo("input_text");
        assertThat(msg.get("content").get(0).get("text").asString()).contains("BOTTOM(하의): ", "JEANS(청바지)");
        JsonNode image = msg.get("content").get(1);
        assertThat(image.get("type").asString()).isEqualTo("input_image");
        assertThat(image.get("detail").asString()).isEqualTo("auto");
        String url = image.get("image_url").asString();
        assertThat(url).startsWith("data:image/jpeg;base64,");
        assertThat(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))).isEqualTo(PHOTO);

        JsonNode format = body.get("text").get("format");
        assertThat(format.get("type").asString()).isEqualTo("json_schema");
        assertThat(format.get("name").asString()).isEqualTo(OpenAiProvider.SCHEMA_NAME);
        assertThat(format.get("strict").asBoolean()).isTrue();
        JsonNode schema = format.get("schema");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse(); // strict 모드 필수
        assertThat(texts(schema.get("required"))).hasSize(9);
        assertThat(texts(schema.get("properties").get("subType").get("enum"))).hasSize(52);
    }

    @Test
    void 응답의_output_text를_검증하고_토큰_수와_모델을_돌려준다() {
        server.reply("/v1/responses", 200, ProviderReplies.openai(JSON.writeValueAsString(ProviderReplies.suggestion()),
                "gpt-test-2026"));
        AiResult r = provider(null, Duration.ofSeconds(5)).analyze(new AiImage(PHOTO, "image/png", "x".repeat(64)), codes);
        assertThat(r.suggestion().subType()).isEqualTo("JEANS");
        assertThat(r.suggestion().color()).isEqualTo("denim");
        assertThat(r.model()).isEqualTo("gpt-test-2026");
        assertThat(r.inputTokens()).isEqualTo(1200);
        assertThat(r.outputTokens()).isEqualTo(300);
        assertThat(JSON.readTree(server.last().body()).has("temperature")).isFalse(); // 비우면 보내지 않는다
    }

    @Test
    void 거절_잘림_허용_목록_밖은_실패() {
        ObjectNode refusal = (ObjectNode) JSON.readTree(ProviderReplies.openai("x", "gpt"));
        ObjectNode content = (ObjectNode) refusal.get("output").get(1).get("content").get(0);
        content.removeAll();
        content.put("type", "refusal").put("refusal", "할 수 없어요");
        server.reply("/v1/responses", 200, JSON.writeValueAsString(refusal));
        assertReason("REFUSAL");

        ObjectNode incomplete = (ObjectNode) JSON.readTree(ProviderReplies.openai("{\"name\":", "gpt"));
        incomplete.put("status", "incomplete");
        incomplete.putObject("incomplete_details").put("reason", "max_output_tokens");
        server.reply("/v1/responses", 200, JSON.writeValueAsString(incomplete));
        assertReason("MAX_TOKENS");

        server.reply("/v1/responses", 200, ProviderReplies.openai(
                JSON.writeValueAsString(ProviderReplies.suggestion().put("subType", "TEE")), "gpt")); // 짝 틀림
        assertReason("INVALID_OUTPUT");
    }

    @Test
    void API_오류는_상태_코드로_남기고_키는_메시지에서_지운다() {
        server.reply("/v1/responses", 401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-test-op***3456. "
                + "full " + KEY + "\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":\"invalid_api_key\"}}");
        assertThatThrownBy(() -> provider(null, Duration.ofSeconds(5)).analyze(image(), codes))
                .isInstanceOf(AiProviderException.class)
                .satisfies(e -> {
                    assertThat(((AiProviderException) e).reason()).isEqualTo("API_401");
                    assertThat(e.getMessage()).contains("Incorrect API key provided").doesNotContain(KEY)
                            .doesNotContain("sk-test").doesNotContain("3456");
                });

        server.reply("/v1/responses", 503, "upstream down");
        assertReason("API_503");
    }

    @Test
    void 시간_초과와_HEIC() {
        server.reply("/v1/responses", 200, ProviderReplies.openai("{}", "gpt"), 1500);
        assertThatThrownBy(() -> provider(null, Duration.ofMillis(300)).analyze(image(), codes))
                .isInstanceOf(AiProviderException.class).extracting("reason").isEqualTo("TIMEOUT");

        server.clear();
        assertThatThrownBy(() -> provider(null, Duration.ofSeconds(5))
                .analyze(new AiImage(PHOTO, "image/heic", "x".repeat(64)), codes))
                .isInstanceOf(AiProviderException.class).extracting("reason").isEqualTo("UNSUPPORTED_MEDIA");
        assertThat(server.requests()).isEmpty();
    }

    private void assertReason(String reason) {
        assertThatThrownBy(() -> provider(null, Duration.ofSeconds(5)).analyze(image(), codes))
                .isInstanceOf(AiProviderException.class).extracting("reason").isEqualTo(reason);
    }

    private static AiImage image() {
        return new AiImage(PHOTO, "image/jpeg", "x".repeat(64));
    }

    static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }
}
