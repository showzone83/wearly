package app.wearly.ai;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.ai.application.service.AiAnalysisService;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestImages;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * DB 공급자 실행(WEARLY_AI_PROVIDER=db)에서 07 문서 3장 규칙을 가짜 공급자 서버로 확인한다. - 쓸 수 있는 공급자를 priority 순으로 시도하고
 * 실패하면 다음으로, 실패도 각각 기록 - 전부 불가면 502 AI_UNAVAILABLE (목업으로 대신하지 않음) - 관리자 시험 분석은 한도에 세지 않고
 * feature=ADMIN_TEST로 기록
 */
class AiProviderFallbackIntegrationTest extends IntegrationTestBase {

  static final String OPENAI_KEY = "sk-fallback-openai-secret";
  static final String GEMINI_KEY = "AIzaFallbackGeminiSecret";
  static final FakeHttpServer OPENAI = new FakeHttpServer();
  static final FakeHttpServer GEMINI = new FakeHttpServer();
  static final String PATH = "/api/v1/wardrobe/items/analyze";

  @DynamicPropertySource
  static void dbMode(DynamicPropertyRegistry registry) {
    registry.add("wearly.ai.provider", () -> "db");
    registry.add("TEST_OPENAI_API_KEY", () -> OPENAI_KEY);
    registry.add("TEST_GEMINI_API_KEY", () -> GEMINI_KEY);
  }

  @AfterAll
  static void stopServers() {
    OPENAI.close();
    GEMINI.close();
  }

  @Autowired JdbcTemplate jdbc;

  @Autowired AiAnalysisService service;

  String adminToken;

  @BeforeEach
  void setUp() {
    // 다른 테스트가 만든 공급자에 흔들리지 않게 모두 끄고 시작한다
    jdbc.update("update tb_ai_provider set enabled = false");
    jdbc.update(
        "delete from tb_ai_provider where provider_id in ('t-openai', 't-gemini', 't-nomodel')");
    OPENAI.clear();
    GEMINI.clear();
    adminToken = admin().accessToken();
  }

  @AfterEach
  void tearDown() {
    jdbc.update(
        "delete from tb_ai_provider where provider_id in ('t-openai', 't-gemini', 't-nomodel')");
    jdbc.update("update tb_ai_provider set enabled = (provider_id = 'claude')");
  }

  @Test
  void 쓸_수_있는_공급자가_없으면_502이고_목업으로_대신하지_않는다() {
    Session s = signup();
    api()
        .upload(PATH, s.accessToken(), "image", photo(1), "a.jpg", "image/jpeg")
        .expectError(502, "AI_UNAVAILABLE");
    Integer logs =
        jdbc.queryForObject(
            "select count(*) from tb_ai_analysis_log where user_id = ?",
            Integer.class,
            Long.parseLong(s.userId()));
    assertThat(logs).isZero();
    assertThat(api().get("/api/v1/admin/ai/types", adminToken).body().get("mockMode").asBoolean())
        .isFalse();
  }

  @Test
  void 앞_공급자가_실패하면_다음_공급자로_넘어가고_실패도_기록한다() {
    create("t-openai", "OPENAI", OPENAI.url(), "TEST_OPENAI_API_KEY", 1);
    create("t-gemini", "GEMINI", GEMINI.url(), "TEST_GEMINI_API_KEY", 2);
    OPENAI.respond(500, "{\"error\":{\"message\":\"upstream down\"}}");
    GEMINI.respond(200, geminiOk());

    Session s = signup();
    Resp r =
        api().upload(PATH, s.accessToken(), "image", photo(2), "a.jpg", "image/jpeg").expect(200);
    assertThat(r.body().get("provider").asString()).isEqualTo("t-gemini");
    assertThat(r.body().get("model").asString()).isEqualTo("gemini-test-001");
    assertThat(r.body().get("remainingToday").asInt()).isEqualTo(2);
    assertThat(r.body().get("suggestion").get("subType").asString()).isEqualTo("KNIT");
    assertThat(OPENAI.requests()).hasSize(1);
    assertThat(OPENAI.last().header("Authorization")).isEqualTo("Bearer " + OPENAI_KEY);
    assertThat(GEMINI.last().header("x-goog-api-key")).isEqualTo(GEMINI_KEY);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select provider, status, error_code, feature, input_tokens from tb_ai_analysis_log "
                + "where user_id = ? order by ai_analysis_log_id",
            Long.parseLong(s.userId()));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("provider", "t-openai")
        .containsEntry("status", "FAILED")
        .containsEntry("error_code", "API_500");
    assertThat(rows.get(1))
        .containsEntry("provider", "t-gemini")
        .containsEntry("status", "SUCCESS")
        .containsEntry("feature", "ITEM_ANALYZE")
        .containsEntry("input_tokens", 900);

    // 우선순위를 바꾸면 Gemini부터, 성공하면 OpenAI는 부르지 않는다
    api()
        .patch("/api/v1/admin/ai/providers/t-gemini", adminToken, Map.of("priority", 0))
        .expect(200);
    OPENAI.clear();
    api().upload(PATH, s.accessToken(), "image", photo(3), "a.jpg", "image/jpeg").expect(200);
    assertThat(OPENAI.requests()).isEmpty();

    // 끈 공급자는 시도하지 않는다
    api()
        .patch("/api/v1/admin/ai/providers/t-gemini", adminToken, Map.of("enabled", false))
        .expect(200);
    api()
        .upload(PATH, s.accessToken(), "image", photo(4), "a.jpg", "image/jpeg")
        .expectError(502, "AI_UNAVAILABLE");
    assertThat(OPENAI.requests()).hasSize(1);
  }

  @Test
  void 전부_실패하면_502이고_한도에_세지_않는다() {
    create("t-openai", "OPENAI", OPENAI.url(), "TEST_OPENAI_API_KEY", 1);
    create("t-gemini", "GEMINI", GEMINI.url(), "TEST_GEMINI_API_KEY", 2);
    OPENAI.respond(503, "{\"error\":{\"message\":\"busy\"}}");
    // 활성 코드 밖 값 → 응답 검증 실패도 다음으로 넘어가는 실패다
    GEMINI.respond(
        200,
        GeminiAiProviderTest.response(
            ClaudeAiProviderTest.validSuggestion().put("color", "fuchsia"), "STOP"));

    Session s = signup();
    for (int i = 0; i < 4; i++) {
      api()
          .upload(PATH, s.accessToken(), "image", photo(10 + i), "a.jpg", "image/jpeg")
          .expectError(502, "AI_UNAVAILABLE");
    }
    long userId = Long.parseLong(s.userId());
    assertThat(service.usedToday(userId)).isZero();
    List<String> reasons =
        jdbc.queryForList(
            "select error_code from tb_ai_analysis_log where user_id = ?"
                + " order by ai_analysis_log_id",
            String.class,
            userId);
    assertThat(reasons).hasSize(8).containsOnly("API_503", "INVALID_OUTPUT");
  }

  @Test
  void 시험_분석은_사용_여부와_관계없이_부르고_한도에_세지_않는다() {
    create("t-openai", "OPENAI", OPENAI.url(), "TEST_OPENAI_API_KEY", 1);
    create("t-gemini", "GEMINI", GEMINI.url(), "TEST_GEMINI_API_KEY", 2);
    api()
        .patch("/api/v1/admin/ai/providers/t-openai", adminToken, Map.of("enabled", false))
        .expect(200);
    OPENAI.respond(200, OpenAiProviderTest.response(ClaudeAiProviderTest.validSuggestion()));
    GEMINI.respond(500, "{\"error\":{\"message\":\"boom\"}}");

    Resp ok =
        api()
            .upload(
                "/api/v1/admin/ai/providers/t-openai/test",
                adminToken,
                "image",
                photo(20),
                "a.jpg",
                "image/jpeg")
            .expect(200);
    assertThat(ok.body().get("ok").asBoolean()).isTrue();
    assertThat(ok.body().get("suggestion").get("name").asString()).isEqualTo("네이비 울 니트");
    assertThat(ok.body().get("inputTokens").asLong()).isEqualTo(1200);
    assertThat(ok.body().get("outputTokens").asLong()).isEqualTo(345);
    assertThat(ok.body().get("model").asString()).isEqualTo("gpt-test-2026-01-01");
    assertThat(ok.body().get("latencyMs").asInt()).isGreaterThanOrEqualTo(0);

    Resp fail =
        api()
            .upload(
                "/api/v1/admin/ai/providers/t-gemini/test",
                adminToken,
                "image",
                photo(21),
                "a.jpg",
                "image/jpeg")
            .expect(200);
    assertThat(fail.body().get("ok").asBoolean()).isFalse();
    assertThat(fail.body().get("errorCode").asString()).isEqualTo("API_500");
    assertThat(fail.body().get("errorMessage").asString()).contains("boom");
    assertThat(fail.body().toString()).doesNotContain(GEMINI_KEY).doesNotContain(OPENAI_KEY);

    // 관리자가 한도(3)보다 많이 시험해도 관리자 자신의 사진 분석 한도는 그대로
    for (int i = 0; i < 4; i++) {
      api()
          .upload(
              "/api/v1/admin/ai/providers/t-openai/test",
              adminToken,
              "image",
              photo(30 + i),
              "a.jpg",
              "image/jpeg")
          .expect(200);
    }
    long adminId = Long.parseLong(admin().userId());
    long before = service.usedToday(adminId);
    Integer tests =
        jdbc.queryForObject(
            "select count(*) from tb_ai_analysis_log where user_id = ? "
                + "and feature = 'ADMIN_TEST'",
            Integer.class,
            adminId);
    assertThat(tests).isGreaterThanOrEqualTo(6);
    assertThat(before).isLessThan(3);

    // 모델이 없으면 호출하지 않고 MODEL_MISSING
    api()
        .post(
            "/api/v1/admin/ai/providers",
            adminToken,
            Map.of(
                "providerId",
                "t-nomodel",
                "type",
                "GEMINI",
                "displayName",
                "모델 없음",
                "apiKeyEnv",
                "TEST_GEMINI_API_KEY"))
        .expect(201);
    Resp noModel =
        api()
            .upload(
                "/api/v1/admin/ai/providers/t-nomodel/test",
                adminToken,
                "image",
                photo(40),
                "a.jpg",
                "image/jpeg")
            .expect(200);
    assertThat(noModel.body().get("ok").asBoolean()).isFalse();
    assertThat(noModel.body().get("errorCode").asString()).isEqualTo("MODEL_MISSING");
    api()
        .upload(
            "/api/v1/admin/ai/providers/nope/test",
            adminToken,
            "image",
            photo(41),
            "a.jpg",
            "image/jpeg")
        .expectError(404, "NOT_FOUND");

    // AI 기록 검색과 상세, 대시보드
    JsonNode logs =
        api()
            .get(
                "/api/v1/admin/ai/logs?providerId=t-openai&feature=ADMIN_TEST"
                    + "&status=SUCCESS&size=2",
                adminToken)
            .expect(200)
            .body();
    assertThat(logs.get("items")).hasSize(2);
    assertThat(logs.get("total").asLong()).isGreaterThanOrEqualTo(5);
    JsonNode row = logs.get("items").get(0);
    assertThat(row.get("userEmail").asString()).isEqualTo(ADMIN_EMAIL);
    assertThat(row.get("resultSummary").asString()).isEqualTo("네이비 울 니트 · TOP/KNIT · navy");
    assertThat(row.get("imageBytes").asInt()).isEqualTo(64);
    JsonNode detail =
        api()
            .get("/api/v1/admin/ai/logs/" + row.get("id").asString(), adminToken)
            .expect(200)
            .body();
    assertThat(detail.get("result").get("subType").asString()).isEqualTo("KNIT");
    assertThat(detail.get("imageSha256").asString()).hasSize(64);
    api()
        .get(
            "/api/v1/admin/ai/logs?userEmail=admin@WEARLY&from=2020-01-01&to=2999-12-31",
            adminToken)
        .expect(200);
    api()
        .get("/api/v1/admin/ai/logs?status=WEIRD", adminToken)
        .expectError(400, "VALIDATION_FAILED");
    api()
        .get("/api/v1/admin/ai/logs?from=yesterday", adminToken)
        .expectError(400, "VALIDATION_FAILED");
    api().get("/api/v1/admin/ai/logs/999999999", adminToken).expectError(404, "NOT_FOUND");

    JsonNode dash = api().get("/api/v1/admin/dashboard", adminToken).expect(200).body();
    assertThat(dash.get("aiToday").get("total").asLong()).isGreaterThanOrEqualTo(7);
    assertThat(dash.get("aiByProvider7d").findValuesAsString("providerId"))
        .contains("t-openai", "t-gemini");

    JsonNode providers = api().get("/api/v1/admin/ai/providers", adminToken).body();
    JsonNode openai = byId(providers, "t-openai");
    assertThat(openai.get("today").get("success").asLong()).isGreaterThanOrEqualTo(5);
    assertThat(providers.toString()).doesNotContain(OPENAI_KEY).doesNotContain(GEMINI_KEY);
  }

  @Test
  void 큰_사진은_긴_변_1024_JPEG로_줄여_공급자에_보내고_보낸_바이트를_기록한다() throws Exception {
    create("t-gemini", "GEMINI", GEMINI.url(), "TEST_GEMINI_API_KEY", 1);
    GEMINI.respond(200, geminiOk());
    Session s = signup();
    long userId = Long.parseLong(s.userId());

    // 3000x2000 PNG → 1024x683 JPEG
    byte[] big = TestImages.png(3000, 2000);
    api().upload(PATH, s.accessToken(), "image", big, "big.png", "image/png").expect(200);
    JsonNode inline = inlineData(GEMINI.last());
    assertThat(inline.get("mimeType").asString()).isEqualTo("image/jpeg");
    byte[] sent = Base64.getDecoder().decode(inline.get("data").asString());
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(sent));
    assertThat(image.getWidth()).isEqualTo(1024);
    assertThat(image.getHeight()).isEqualTo(683);
    Map<String, Object> log = lastLog(userId);
    assertThat(((Number) log.get("image_bytes")).intValue()).isEqualTo(sent.length);
    assertThat(log.get("image_sha256")).isEqualTo(AiAnalysisService.sha256(sent));

    // 긴 변이 1024 이하면 올린 바이트·형식 그대로 보낸다
    byte[] small = TestImages.png(800, 600);
    api().upload(PATH, s.accessToken(), "image", small, "small.png", "image/png").expect(200);
    JsonNode same = inlineData(GEMINI.last());
    assertThat(same.get("mimeType").asString()).isEqualTo("image/png");
    assertThat(Base64.getDecoder().decode(same.get("data").asString())).isEqualTo(small);
    assertThat(((Number) lastLog(userId).get("image_bytes")).intValue()).isEqualTo(small.length);
  }

  private static JsonNode inlineData(FakeHttpServer.Request request) {
    return TestApi.JSON
        .readTree(request.body())
        .get("contents")
        .get(0)
        .get("parts")
        .get(0)
        .get("inlineData");
  }

  private Map<String, Object> lastLog(long userId) {
    return jdbc.queryForMap(
        "select image_bytes, image_sha256 from tb_ai_analysis_log where user_id = ?"
            + " order by ai_analysis_log_id desc limit 1",
        userId);
  }

  private void create(String id, String type, String baseUrl, String env, int priority) {
    api()
        .post(
            "/api/v1/admin/ai/providers",
            adminToken,
            Map.of(
                "providerId",
                id,
                "type",
                type,
                "displayName",
                id,
                "model",
                type.equals("OPENAI") ? "gpt-test" : "gemini-test",
                "apiKeyEnv",
                env,
                "enabled",
                true,
                "priority",
                priority,
                "options",
                Map.of("baseUrl", baseUrl, "timeoutSeconds", 5)))
        .expect(201);
  }

  private static String geminiOk() {
    return GeminiAiProviderTest.response(ClaudeAiProviderTest.validSuggestion(), "STOP");
  }

  private static byte[] photo(long seed) {
    byte[] b = new byte[64];
    new Random(seed).nextBytes(b);
    b[0] = (byte) 0xFF;
    b[1] = (byte) 0xD8;
    b[2] = (byte) 0xFF;
    return b;
  }

  private static JsonNode byId(JsonNode list, String id) {
    for (JsonNode p : list) {
      if (p.get("providerId").asString().equals(id)) {
        return p;
      }
    }
    return null;
  }
}
