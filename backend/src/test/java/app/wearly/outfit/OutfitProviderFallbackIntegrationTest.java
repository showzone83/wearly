package app.wearly.outfit;

import static app.wearly.outfit.OutfitTestData.PATH;
import static app.wearly.outfit.OutfitTestData.context;
import static app.wearly.outfit.OutfitTestData.request;
import static app.wearly.outfit.OutfitTestData.wardrobe;
import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DB 공급자 실행에서 코디 추천의 공급자 시도 순서를 가짜 OpenAI·Gemini·Claude 서버로 확인한다 (09 문서 3·4장).
 *
 * <ul>
 *   <li>공급자 오류도, 검증 뒤 남은 후보 0개도 실패로 기록하고 다음 공급자로 넘어간다
 *   <li>세 공급자 모두 이미지 없는 구조화 JSON 요청(시스템 지시 + 텍스트 + 스키마)을 보낸다
 *   <li>전부 실패하면 502 AI_UNAVAILABLE이고 한도에 세지 않는다
 * </ul>
 */
class OutfitProviderFallbackIntegrationTest extends IntegrationTestBase {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String OPENAI_KEY = "sk-outfit-openai-secret";
  static final String GEMINI_KEY = "AIzaOutfitGeminiSecret";
  static final String CLAUDE_KEY = "sk-ant-outfit-claude-secret";
  static final FakeHttpServer OPENAI = new FakeHttpServer();
  static final FakeHttpServer GEMINI = new FakeHttpServer();
  static final FakeHttpServer CLAUDE = new FakeHttpServer();
  static final List<String> IDS = List.of("o-openai", "o-gemini", "o-claude");

  @DynamicPropertySource
  static void dbMode(DynamicPropertyRegistry registry) {
    registry.add("wearly.ai.provider", () -> "db");
    registry.add("TEST_OUTFIT_OPENAI_API_KEY", () -> OPENAI_KEY);
    registry.add("TEST_OUTFIT_GEMINI_API_KEY", () -> GEMINI_KEY);
    registry.add("TEST_OUTFIT_CLAUDE_API_KEY", () -> CLAUDE_KEY);
  }

  @AfterAll
  static void stopServers() {
    OPENAI.close();
    GEMINI.close();
    CLAUDE.close();
  }

  @Autowired JdbcTemplate jdbc;

  String adminToken;

  @BeforeEach
  void setUp() {
    jdbc.update("update tb_ai_provider set enabled = false");
    jdbc.update(
        "delete from tb_ai_provider where provider_id in ('o-openai', 'o-gemini', 'o-claude')");
    OPENAI.clear();
    GEMINI.clear();
    CLAUDE.clear();
    adminToken = admin().accessToken();
  }

  @AfterEach
  void tearDown() {
    jdbc.update(
        "delete from tb_ai_provider where provider_id in ('o-openai', 'o-gemini', 'o-claude')");
    jdbc.update("update tb_ai_provider set enabled = (provider_id = 'claude')");
  }

  @Test
  void 실패하거나_남은_후보가_0개면_다음_공급자로_넘어간다() {
    create("o-openai", "OPENAI", OPENAI.url(), "TEST_OUTFIT_OPENAI_API_KEY", 1);
    create("o-gemini", "GEMINI", GEMINI.url(), "TEST_OUTFIT_GEMINI_API_KEY", 2);
    create("o-claude", "CLAUDE", CLAUDE.url(), "TEST_OUTFIT_CLAUDE_API_KEY", 3);
    Session s = signup();
    Map<String, String> ids = wardrobe(api(), s.accessToken());
    Session other = signup();
    Map<String, String> otherIds = wardrobe(api(), other.accessToken());

    OPENAI.respond(500, "{\"error\":{\"message\":\"upstream down\"}}");
    // Gemini: 남의 옷·칸 불일치·신발 없음 → 모두 버려져 남은 후보 0개
    GEMINI.respond(
        200,
        gemini(
            candidates(
                slots(otherIds.get("화이트 셔츠"), ids.get("차콜 슬랙스"), null, ids.get("블랙 로퍼")),
                slots(ids.get("차콜 슬랙스"), ids.get("베이지 와이드"), null, ids.get("블랙 로퍼")),
                slots(ids.get("화이트 셔츠"), ids.get("차콜 슬랙스"), null, null))));
    // Claude: 정상 1개 + 원피스·상의 혼합 1개
    CLAUDE.respond(
        200,
        claude(
            candidates(
                slots(ids.get("네이비 니트"), ids.get("차콜 슬랙스"), null, ids.get("블랙 로퍼")),
                slots(ids.get("네이비 니트"), null, ids.get("블랙 셔츠 원피스"), ids.get("블랙 로퍼")))));

    JsonNode body = api().post(PATH, s.accessToken(), request(context())).expect(200).body();
    assertThat(body.get("provider").asString()).isEqualTo("o-claude");
    assertThat(body.get("model").asString()).isEqualTo("claude-test-model");
    assertThat(body.get("remainingToday").asInt()).isEqualTo(2);
    assertThat(body.get("candidates")).hasSize(1);
    JsonNode first = body.get("candidates").get(0);
    assertThat(first.get("items").get("TOP").asString()).isEqualTo(ids.get("네이비 니트"));
    assertThat(first.get("items").has("DRESS")).isFalse();
    assertThat(first.get("aiScores").get("weather").asInt()).isEqualTo(100);

    // OpenAI: Responses API, instructions + input_text, text.format = json_schema(strict)
    JsonNode openai = JSON.readTree(OPENAI.last().body());
    assertThat(OPENAI.last().path()).isEqualTo("/responses");
    assertThat(OPENAI.last().header("Authorization")).isEqualTo("Bearer " + OPENAI_KEY);
    assertThat(openai.get("instructions").asString()).contains("색상 조화 30%");
    JsonNode content = openai.get("input").get(0).get("content");
    assertThat(content).hasSize(1);
    assertThat(content.get(0).get("type").asString()).isEqualTo("input_text");
    assertThat(content.get(0).get("text").asString()).contains("[SHOES]");
    JsonNode format = openai.get("text").get("format");
    assertThat(format.get("type").asString()).isEqualTo("json_schema");
    assertThat(format.get("name").asString()).isEqualTo("outfit_candidates");
    assertThat(format.get("strict").asBoolean()).isTrue();
    assertSlotsEnum(format.get("schema"), ids);
    assertThat(openai.get("store").asBoolean()).isFalse();

    // Gemini: systemInstruction + text part, responseFormat.text.schema
    JsonNode gemini = JSON.readTree(GEMINI.last().body());
    assertThat(GEMINI.last().path()).isEqualTo("/v1beta/models/gemini-test:generateContent");
    assertThat(GEMINI.last().header("x-goog-api-key")).isEqualTo(GEMINI_KEY);
    assertThat(gemini.get("systemInstruction").get("parts").get(0).get("text").asString())
        .contains("다양성 10%");
    JsonNode parts = gemini.get("contents").get(0).get("parts");
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).has("inlineData")).isFalse();
    JsonNode responseFormat = gemini.get("generationConfig").get("responseFormat").get("text");
    assertThat(responseFormat.get("mimeType").asString()).isEqualTo("APPLICATION_JSON");
    assertSlotsEnum(responseFormat.get("schema"), ids);

    // Claude: Messages API, system + user 텍스트, output_config.format = json_schema, 서버 측 폴백
    JsonNode claude = JSON.readTree(CLAUDE.last().body());
    assertThat(CLAUDE.last().path()).isEqualTo("/v1/messages");
    assertThat(CLAUDE.last().header("x-api-key")).isEqualTo(CLAUDE_KEY);
    assertThat(CLAUDE.last().header("anthropic-beta")).contains("server-side-fallback-2026-07-01");
    assertThat(claude.get("model").asString()).isEqualTo("claude-test-model");
    assertThat(claude.get("system").toString()).contains("점수 기준");
    JsonNode message = claude.get("messages").get(0);
    assertThat(message.get("role").asString()).isEqualTo("user");
    assertThat(message.get("content").toString()).contains("## 옷장").doesNotContain("\"image\"");
    JsonNode outputConfig = claude.get("output_config");
    assertThat(outputConfig.get("format").get("type").asString()).isEqualTo("json_schema");
    assertSlotsEnum(outputConfig.get("format").get("schema"), ids);
    assertThat(claude.get("fallbacks").asString()).isEqualTo("default");

    // 기록: 실패 2 + 성공 1, 모두 OUTFIT_RECOMMEND
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select provider, status, error_code, feature, input_tokens from tb_ai_analysis_log"
                + " where user_id = ? order by ai_analysis_log_id",
            Long.parseLong(s.userId()));
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0))
        .containsEntry("provider", "o-openai")
        .containsEntry("status", "FAILED")
        .containsEntry("error_code", "API_500")
        .containsEntry("feature", "OUTFIT_RECOMMEND");
    assertThat(rows.get(1))
        .containsEntry("provider", "o-gemini")
        .containsEntry("status", "FAILED")
        .containsEntry("error_code", "REJECTED_OUTPUT")
        .containsEntry("input_tokens", 900);
    assertThat(rows.get(2))
        .containsEntry("provider", "o-claude")
        .containsEntry("status", "SUCCESS")
        .containsEntry("feature", "OUTFIT_RECOMMEND")
        .containsEntry("input_tokens", 1500);

    // 관리자 공급자 today·대시보드 공급자별 통계에 코디 추천도 들어간다
    JsonNode providers = api().get("/api/v1/admin/ai/providers", adminToken).body();
    assertThat(byId(providers, "providerId", "o-claude").get("today").get("success").asLong())
        .isEqualTo(1);
    assertThat(byId(providers, "providerId", "o-gemini").get("today").get("failed").asLong())
        .isEqualTo(1);
    JsonNode stats = api().get("/api/v1/admin/dashboard", adminToken).body().get("aiByProvider7d");
    assertThat(byId(stats, "providerId", "o-openai").get("failed").asLong())
        .isGreaterThanOrEqualTo(1);
    // 키는 응답·기록 어디에도 없다
    assertThat(providers.toString()).doesNotContain(OPENAI_KEY).doesNotContain(CLAUDE_KEY);
  }

  @Test
  void 전부_실패하면_502이고_한도에_세지_않는다() {
    create("o-openai", "OPENAI", OPENAI.url(), "TEST_OUTFIT_OPENAI_API_KEY", 1);
    create("o-gemini", "GEMINI", GEMINI.url(), "TEST_OUTFIT_GEMINI_API_KEY", 2);
    Session s = signup();
    Map<String, String> ids = wardrobe(api(), s.accessToken());
    OPENAI.respond(429, "{\"error\":{\"message\":\"rate limited\"}}");
    // 형식은 맞지만 남은 후보 0개 (모르는 id)
    GEMINI.respond(
        200, gemini(candidates(slots("123456789", ids.get("차콜 슬랙스"), null, ids.get("블랙 로퍼")))));
    TestApi.Resp r =
        api().post(PATH, s.accessToken(), request(context())).expectError(502, "AI_UNAVAILABLE");
    assertThat(r.body().get("message").asString()).contains("코디 추천");
    List<String> codes =
        jdbc.queryForList(
            "select error_code from tb_ai_analysis_log where user_id = ?"
                + " order by ai_analysis_log_id",
            String.class,
            Long.parseLong(s.userId()));
    assertThat(codes).containsExactly("API_429", "REJECTED_OUTPUT");

    // 실패는 한도에 세지 않는다: Gemini가 살아나면 remainingToday = 3 - 1
    GEMINI.respond(
        200,
        gemini(candidates(slots(ids.get("화이트 셔츠"), ids.get("차콜 슬랙스"), null, ids.get("블랙 로퍼")))));
    assertThat(
            api()
                .post(PATH, s.accessToken(), request(context()))
                .expect(200)
                .body()
                .get("remainingToday")
                .asInt())
        .isEqualTo(2);
  }

  @Test
  void 쓸_수_있는_공급자가_없으면_502이고_목업으로_대신하지_않는다() {
    Session s = signup();
    wardrobe(api(), s.accessToken());
    api().post(PATH, s.accessToken(), request(context())).expectError(502, "AI_UNAVAILABLE");
    Integer logs =
        jdbc.queryForObject(
            "select count(*) from tb_ai_analysis_log where user_id = ?",
            Integer.class,
            Long.parseLong(s.userId()));
    assertThat(logs).isZero();
  }

  /** 칸별 enum이 그 회원의 그 칸 아이템만이고 세탁 중인 옷은 빠진다 */
  private static void assertSlotsEnum(JsonNode schema, Map<String, String> ids) {
    JsonNode slots =
        schema
            .get("properties")
            .get("candidates")
            .get("items")
            .get("properties")
            .get("items")
            .get("properties");
    JsonNode top = slots.get("TOP").get("anyOf").get(0).get("enum");
    assertThat(texts(top)).containsExactly(ids.get("화이트 셔츠"), ids.get("네이비 니트"));
    assertThat(texts(slots.get("SHOES").get("enum")))
        .containsExactly(ids.get("블랙 로퍼"), ids.get("화이트 스니커즈"));
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
                switch (type) {
                  case "OPENAI" -> "gpt-test";
                  case "GEMINI" -> "gemini-test";
                  default -> "claude-test-model";
                },
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

  private static Map<String, Object> slots(String top, String bottom, String dress, String shoes) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("TOP", top);
    m.put("BOTTOM", bottom);
    m.put("DRESS", dress);
    m.put("OUTER", null);
    m.put("SHOES", shoes);
    m.put("BAG", null);
    return m;
  }

  @SafeVarargs
  private static String candidates(Map<String, Object>... items) {
    ObjectNode root = JSON.createObjectNode();
    var list = root.putArray("candidates");
    for (Map<String, Object> i : items) {
      ObjectNode c = list.addObject();
      c.set("items", JSON.valueToTree(i));
      c.put("reason", "차분한 색으로 단정하게 맞췄어요.");
      c.putObject("aiScores")
          .put("color", 80)
          .put("occasion", 75)
          .put("weather", 100)
          .put("taste", 70)
          .put("diversity", 60);
    }
    return JSON.writeValueAsString(root);
  }

  private static String gemini(String text) {
    ObjectNode r = JSON.createObjectNode();
    ObjectNode candidate = r.putArray("candidates").addObject();
    candidate
        .putObject("content")
        .put("role", "model")
        .putArray("parts")
        .addObject()
        .put("text", text);
    candidate.put("finishReason", "STOP");
    r.putObject("usageMetadata").put("promptTokenCount", 900).put("candidatesTokenCount", 70);
    r.put("modelVersion", "gemini-test-001");
    return JSON.writeValueAsString(r);
  }

  private static String claude(String text) {
    ObjectNode m = JSON.createObjectNode();
    m.put("id", "msg_test");
    m.put("type", "message");
    m.put("role", "assistant");
    m.put("model", "claude-test-model");
    m.putArray("content").addObject().put("type", "text").put("text", text);
    m.put("stop_reason", "end_turn");
    m.putNull("stop_sequence");
    m.putObject("usage").put("input_tokens", 1500).put("output_tokens", 80);
    return JSON.writeValueAsString(m);
  }

  private static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asString()));
    return out;
  }

  private static JsonNode byId(JsonNode list, String field, String id) {
    for (JsonNode p : list) {
      if (p.get(field).asString().equals(id)) {
        return p;
      }
    }
    throw new AssertionError(id + " 없음: " + list);
  }
}
