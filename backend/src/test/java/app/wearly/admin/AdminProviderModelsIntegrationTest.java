package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

/**
 * 07 문서 4장: 공급자 모델 목록 GET /admin/ai/providers/{id}/models. 실제 제공사 대신 로컬 가짜 서버(baseUrl)를 쓴다. 키 없음 →
 * CATALOG, 키 있음 → LIVE(10분 캐시, refresh=true), 실패 → CATALOG + MODEL_LIST_FAILED, 권한, 변경 이력 없음.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminProviderModelsIntegrationTest extends IntegrationTestBase {

  static final FakeHttpServer SERVER = new FakeHttpServer();
  static final String PATH = "/api/v1/admin/ai/providers/%s/models";

  /** IntegrationTestBase의 TEST_CRUD_API_KEY 값 */
  static final String ENV_KEY = "test-crud-key-value";

  static final String OPENAI_MODELS =
      "{\"object\":\"list\",\"data\":["
          + "{\"id\":\"gpt-5.1\",\"object\":\"model\",\"created\":1760000000},"
          + "{\"id\":\"o4-mini\",\"object\":\"model\",\"created\":1744000000},"
          + "{\"id\":\"text-embedding-3-large\",\"object\":\"model\",\"created\":1705953180},"
          + "{\"id\":\"whisper-1\",\"object\":\"model\",\"created\":1677532384}]}";

  @AfterAll
  static void stop() {
    SERVER.close();
  }

  String admin;

  @BeforeEach
  void setUp() {
    admin = admin().accessToken();
    SERVER.clear();
    SERVER.respond(200, OPENAI_MODELS);
  }

  String provider(String type, String env) {
    String id = "m-" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> body = new HashMap<>();
    body.put("providerId", id);
    body.put("type", type);
    body.put("displayName", id);
    body.put("apiKeyEnv", env);
    body.put(
        "options",
        Map.of(
            "baseUrl",
            type.equals("OPENAI") ? SERVER.url() + "/v1" : SERVER.url(),
            "timeoutSeconds",
            5));
    api().post("/api/v1/admin/ai/providers", admin, body).expect(201);
    return id;
  }

  JsonNode models(String id, String query) {
    return api().get(PATH.formatted(id) + query, admin).expect(200).body();
  }

  int auditCount() {
    return api()
        .get("/api/v1/admin/audit-logs?targetType=AI_PROVIDER&size=1", admin)
        .expect(200)
        .body()
        .get("total")
        .asInt();
  }

  @Test
  void 키가_없으면_제공사를_부르지_않고_내장_목록과_NO_API_KEY() {
    // 초기 claude 행: 테스트에서는 ANTHROPIC_API_KEY를 비워 둔다
    JsonNode r = models("claude", "");
    assertThat(SERVER.requests()).isEmpty();
    assertThat(r.get("source").asString()).isEqualTo("CATALOG");
    assertThat(r.get("fetchedAt").asString()).endsWith("Z");
    assertThat(r.get("error").get("code").asString()).isEqualTo("NO_API_KEY");
    assertThat(r.get("error").get("message").asString()).isNotBlank();
    assertThat(r.get("models").findValuesAsString("id"))
        .containsExactly("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5");
    JsonNode sonnet = r.get("models").get(0);
    assertThat(sonnet.get("displayName").asString()).isEqualTo("Claude Sonnet 5.5");
    assertThat(sonnet.get("recommended").asBoolean()).isTrue();
    assertThat(sonnet.get("createdAt").isNull()).isTrue();
    assertThat(r.get("models").get(1).get("recommended").asBoolean()).isFalse();

    // 내장 목록이 없는 종류(OPENAI)는 빈 목록 → 화면에서 직접 입력
    String openai = provider("OPENAI", "MISSING_TEST_API_KEY");
    JsonNode empty = models(openai, "?refresh=true");
    assertThat(empty.get("source").asString()).isEqualTo("CATALOG");
    assertThat(empty.get("models").isEmpty()).isTrue();
    assertThat(SERVER.requests()).isEmpty();
  }

  @Test
  void 키가_있으면_제공사_목록을_거르고_10분_캐시하며_refresh로_다시_받는다(CapturedOutput output) {
    String id = provider("OPENAI", "TEST_CRUD_API_KEY");
    final int audits = auditCount();

    JsonNode r = models(id, "");
    assertThat(r.get("source").asString()).isEqualTo("LIVE");
    assertThat(r.get("error").isNull()).isTrue();
    assertThat(r.get("models").findValuesAsString("id")).containsExactly("gpt-5.1", "o4-mini");
    assertThat(r.get("models").get(0).get("createdAt").asString())
        .isEqualTo("2025-10-09T08:53:20Z");
    assertThat(r.get("models").get(0).get("displayName").asString()).isEqualTo("gpt-5.1");
    assertThat(SERVER.requests()).hasSize(1);
    assertThat(SERVER.last().path()).isEqualTo("/v1/models");
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer " + ENV_KEY);

    // 캐시: 다시 불러도 제공사를 부르지 않고 fetchedAt도 같다
    JsonNode again = models(id, "?refresh=false");
    assertThat(SERVER.requests()).hasSize(1);
    assertThat(again.get("fetchedAt").asString()).isEqualTo(r.get("fetchedAt").asString());

    // refresh=true: 캐시를 무시. 실패하면 내장 목록(OPENAI는 빈 목록) + MODEL_LIST_FAILED
    SERVER.respond(500, "{\"error\":{\"message\":\"upstream boom " + ENV_KEY + "\"}}");
    JsonNode failed = models(id, "?refresh=true");
    assertThat(SERVER.requests()).hasSize(2);
    assertThat(failed.get("source").asString()).isEqualTo("CATALOG");
    assertThat(failed.get("error").get("code").asString()).isEqualTo("MODEL_LIST_FAILED");
    assertThat(failed.get("error").get("message").asString()).contains("HTTP 500");
    assertThat(failed.toString()).doesNotContain(ENV_KEY).doesNotContain("upstream boom");

    // 실패는 캐시하지 않는다 → 다음 요청에서 다시 받는다
    SERVER.respond(200, OPENAI_MODELS);
    assertThat(models(id, "").get("source").asString()).isEqualTo("LIVE");
    assertThat(SERVER.requests()).hasSize(3);

    // 공급자를 수정하면 캐시를 비운다 (종류·키 이름·API 주소가 바뀔 수 있다)
    api()
        .patch("/api/v1/admin/ai/providers/" + id, admin, Map.of("displayName", "바꾼 이름"))
        .expect(200);
    int afterPatch = auditCount();
    models(id, "");
    assertThat(SERVER.requests()).hasSize(4);

    // 읽기라 변경 이력이 늘지 않는다 (수정 한 번만)
    assertThat(afterPatch).isEqualTo(audits + 1);
    assertThat(auditCount()).isEqualTo(afterPatch);
    assertThat(output.getAll()).doesNotContain(ENV_KEY);
  }

  @Test
  void Claude와_Gemini_제공사_목록() {
    String claude = provider("CLAUDE", "TEST_CRUD_API_KEY");
    SERVER.respond(
        200,
        "{\"data\":[{\"type\":\"model\",\"id\":\"claude-opus-5-5\",\"display_name\":\"Claude Opus"
            + " 5.5\",\"created_at\":\"2026-08-01T00:00:00Z\"},{\"type\":\"model\",\"id\":"
            + "\"claude-sonnet-5-5\",\"display_name\":\"Claude Sonnet 5.5\",\"created_at\":"
            + "\"2026-09-01T00:00:00Z\"},{\"type\":\"model\",\"id\":\"claude-preview-x\","
            + "\"display_name\":\"Preview X\",\"created_at\":\"2026-10-01T00:00:00Z\"}],"
            + "\"has_more\":false}");
    JsonNode c = models(claude, "");
    assertThat(SERVER.last().path()).isEqualTo("/v1/models");
    assertThat(SERVER.last().header("x-api-key")).isEqualTo(ENV_KEY);
    assertThat(c.get("source").asString()).isEqualTo("LIVE");
    // 추천(내장 목록 표시) 먼저, 그다음 createdAt 최신순
    assertThat(c.get("models").findValuesAsString("id"))
        .containsExactly("claude-sonnet-5-5", "claude-preview-x", "claude-opus-5-5");
    assertThat(c.get("models").get(0).get("recommended").asBoolean()).isTrue();
    assertThat(c.get("models").get(2).get("createdAt").asString())
        .isEqualTo("2026-08-01T00:00:00Z");

    String gemini = provider("GEMINI", "TEST_CRUD_API_KEY");
    SERVER.respond(
        200,
        "{\"models\":[{\"name\":\"models/gemini-2.5-flash\",\"displayName\":\"Gemini 2.5"
            + " Flash\",\"supportedGenerationMethods\":[\"generateContent\",\"countTokens\"]},"
            + "{\"name\":\"models/text-embedding-004\",\"supportedGenerationMethods\":"
            + "[\"embedContent\"]}]}");
    JsonNode g = models(gemini, "");
    assertThat(SERVER.last().path()).isEqualTo("/v1beta/models");
    assertThat(SERVER.last().header("x-goog-api-key")).isEqualTo(ENV_KEY);
    assertThat(g.get("models").findValuesAsString("id")).containsExactly("gemini-2.5-flash");
    assertThat(g.get("models").get(0).get("displayName").asString()).isEqualTo("Gemini 2.5 Flash");
    assertThat(g.get("models").get(0).get("createdAt").isNull()).isTrue();
  }

  @Test
  void 권한과_없는_공급자() {
    api().get(PATH.formatted("claude"), signup().accessToken()).expectError(403, "FORBIDDEN");
    api().get(PATH.formatted("claude"), null).expectError(401, "UNAUTHORIZED");
    api().get(PATH.formatted("nope"), admin).expectError(404, "NOT_FOUND");
    assertThat(SERVER.requests()).isEmpty();
  }

  @Test
  void 종류_목록에_내장_모델이_들어_있다() {
    JsonNode types = api().get("/api/v1/admin/ai/types", admin).expect(200).body().get("types");
    List<String> claudeModels = new ArrayList<>();
    for (JsonNode t : types) {
      if (t.get("type").asString().equals("CLAUDE")) {
        t.get("models").forEach(m -> claudeModels.add(m.get("id").asString()));
        assertThat(t.get("models").get(0).get("recommended").asBoolean()).isTrue();
        assertThat(t.get("models").get(0).get("displayName").asString())
            .isEqualTo("Claude Sonnet 5.5");
      } else {
        assertThat(t.get("models").isArray()).isTrue();
        assertThat(t.get("models").isEmpty()).isTrue();
      }
    }
    assertThat(claudeModels)
        .containsExactly("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5");
  }
}
