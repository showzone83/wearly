package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * 07 문서 6장: AI 키 웹 입력 (쓰기 전용 · 암호화 저장). WEARLY_SECRET_KEY를 넣은 컨텍스트. 키 값은 어떤 응답·로그·감사 로그에도 남지 않는다
 * (서버 출력을 잡아 확인한다).
 */
@ExtendWith(OutputCaptureExtension.class)
class ProviderApiKeyIntegrationTest extends IntegrationTestBase {

  static final String MASTER_KEY = masterKey();
  static final FakeHttpServer SERVER = new FakeHttpServer();
  static final String KEY_PATH = "/api/v1/admin/ai/providers/%s/api-key";

  @DynamicPropertySource
  static void secretStore(DynamicPropertyRegistry registry) {
    registry.add("wearly.secret-key", () -> MASTER_KEY);
  }

  @AfterAll
  static void stop() {
    SERVER.close();
  }

  @Autowired JdbcTemplate jdbc;

  String admin;

  @BeforeEach
  void setUp() {
    admin = admin().accessToken();
    SERVER.clear();
    SERVER.respond(200, "{\"data\":[]}");
  }

  static String masterKey() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getEncoder().encodeToString(b);
  }

  static String newKey(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "");
  }

  String provider(String type, String env) {
    String id = "k-" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> body = new HashMap<>();
    body.put("providerId", id);
    body.put("type", type);
    body.put("displayName", id);
    body.put("model", "test-model");
    body.put("apiKeyEnv", env);
    body.put("enabled", true);
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

  Resp put(String id, Map<String, Object> body) {
    return api().put(KEY_PATH.formatted(id), admin, body);
  }

  Resp delete(String id, Object body) {
    return api().exchange(HttpMethod.DELETE, KEY_PATH.formatted(id), admin, body);
  }

  static Map<String, Object> keyBody(String key, String password, Boolean verify) {
    Map<String, Object> m = new HashMap<>();
    m.put("apiKey", key);
    m.put("currentPassword", password);
    if (verify != null) {
      m.put("verify", verify);
    }
    return m;
  }

  JsonNode row(String id) {
    for (JsonNode p : api().get("/api/v1/admin/ai/providers", admin).expect(200).body()) {
      if (p.get("providerId").asString().equals(id)) {
        return p;
      }
    }
    return null;
  }

  @Test
  void 저장_교체_삭제와_키_출처_우선순위(CapturedOutput output) {
    // 환경변수 키가 있는 공급자: ENV → (웹 키) DB → (삭제) ENV
    String id = provider("OPENAI", "TEST_CRUD_API_KEY");
    JsonNode before = row(id);
    assertThat(before.get("keySource").asString()).isEqualTo("ENV");
    assertThat(before.get("keyConfigured").asBoolean()).isTrue();
    assertThat(before.get("keyLast4").isNull()).isTrue();
    assertThat(before.get("keyUpdatedAt").isNull()).isTrue();
    assertThat(before.get("keyUpdatedBy").isNull()).isTrue();
    assertThat(before.get("secretStoreEnabled").asBoolean()).isTrue();

    String first = newKey("sk-first-");
    Resp saved = put(id, keyBody("  " + first + "\n", ADMIN_PASSWORD, false)).expect(200);
    assertThat(saved.body().get("keySource").asString()).isEqualTo("DB");
    assertThat(saved.body().get("keyLast4").asString())
        .isEqualTo(first.substring(first.length() - 4));
    assertThat(saved.body().get("keyUpdatedBy").asString()).isEqualTo(ADMIN_EMAIL);
    assertThat(saved.body().get("keyUpdatedAt").asString()).endsWith("Z");
    assertThat(saved.body().get("usable").asBoolean()).isTrue();
    assertThat(SERVER.requests()).isEmpty(); // verify=false면 확인하지 않는다
    assertThat(row(id).get("keySource").asString()).isEqualTo("DB");

    Map<String, Object> stored =
        jdbc.queryForMap(
            "select ciphertext, nonce, key_version, last4 from tb_ai_provider_secret"
                + " where provider_id = ?",
            id);
    byte[] firstNonce = (byte[]) stored.get("nonce");
    assertThat(firstNonce).hasSize(12);
    assertThat(((Number) stored.get("key_version")).intValue()).isEqualTo(1);
    assertThat(new String((byte[]) stored.get("ciphertext"), StandardCharsets.ISO_8859_1))
        .doesNotContain(first);

    // 교체: 끝 4자·nonce가 바뀐다. 시험 분석은 DB 키로 부른다
    String second = newKey("sk-second-");
    put(id, keyBody(second, ADMIN_PASSWORD, false)).expect(200);
    byte[] secondNonce =
        jdbc.queryForObject(
            "select nonce from tb_ai_provider_secret where provider_id = ?", byte[].class, id);
    assertThat(secondNonce).isNotEqualTo(firstNonce);
    assertThat(row(id).get("keyLast4").asString()).isEqualTo(second.substring(second.length() - 4));
    SERVER.respond(500, "{\"error\":{\"message\":\"down\"}}");
    api()
        .upload(
            "/api/v1/admin/ai/providers/" + id + "/test",
            admin,
            "image",
            photo(),
            "a.jpg",
            "image/jpeg")
        .expect(200);
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer " + second);

    // 삭제: 환경변수 키로 되돌아간다
    Resp removed = delete(id, Map.of("currentPassword", ADMIN_PASSWORD)).expect(200);
    assertThat(removed.body().get("keySource").asString()).isEqualTo("ENV");
    assertThat(removed.body().get("keyLast4").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tb_ai_provider_secret where provider_id = ?",
                Integer.class,
                id))
        .isZero();
    // 지울 웹 키가 없어도 200 (변경 이력은 남기지 않는다)
    delete(id, Map.of("currentPassword", ADMIN_PASSWORD)).expect(200);

    // 환경변수 키가 없는 공급자: NONE → DB
    String none = provider("GEMINI", "MISSING_TEST_API_KEY");
    assertThat(row(none).get("keySource").asString()).isEqualTo("NONE");
    assertThat(row(none).get("usable").asBoolean()).isFalse();
    assertThat(
            put(none, keyBody(newKey("AIza-third-"), ADMIN_PASSWORD, false))
                .expect(200)
                .body()
                .get("keySource")
                .asString())
        .isEqualTo("DB");

    // 변경 이력: before·after에는 keySource·keyLast4만
    List<JsonNode> audits = new ArrayList<>();
    api()
        .get("/api/v1/admin/audit-logs?targetType=AI_PROVIDER&size=100", admin)
        .body()
        .get("items")
        .forEach(
            a -> {
              if (a.get("targetId").asString().equals(id)
                  && a.get("action").asString().startsWith("AI_PROVIDER_KEY")) {
                audits.add(a);
              }
            });
    assertThat(audits)
        .extracting(a -> a.get("action").asString())
        .containsExactly("AI_PROVIDER_KEY_DELETE", "AI_PROVIDER_KEY_SET", "AI_PROVIDER_KEY_SET");
    for (JsonNode a : audits) {
      assertThat(a.get("before").propertyNames())
          .containsExactlyInAnyOrder("keySource", "keyLast4");
      assertThat(a.get("after").propertyNames()).containsExactlyInAnyOrder("keySource", "keyLast4");
    }
    assertThat(audits.get(2).get("before").get("keySource").asString()).isEqualTo("ENV");
    assertThat(audits.get(2).get("after").get("keySource").asString()).isEqualTo("DB");
    assertThat(audits.get(0).get("after").get("keySource").asString()).isEqualTo("ENV");

    String everything =
        api().get("/api/v1/admin/audit-logs?size=100", admin).body().toString()
            + api().get("/api/v1/admin/ai/providers", admin).body();
    assertThat(everything).doesNotContain(first).doesNotContain(second);
    assertThat(output.getAll()).doesNotContain(first).doesNotContain(second);
  }

  @Test
  void 재확인_실패_403과_키_형식_400(CapturedOutput output) {
    String id = provider("OPENAI", "MISSING_TEST_API_KEY");
    String key = newKey("sk-reauth-");
    put(id, keyBody(key, "wrong-password", false)).expectError(403, "REAUTH_FAILED");
    put(id, keyBody(key, null, false)).expectError(403, "REAUTH_FAILED");
    delete(id, Map.of("currentPassword", "wrong-password")).expectError(403, "REAUTH_FAILED");
    delete(id, null).expectError(403, "REAUTH_FAILED");
    // 관리자가 아니면 403 FORBIDDEN
    api()
        .put(KEY_PATH.formatted(id), signup().accessToken(), keyBody(key, "pass1234", false))
        .expectError(403, "FORBIDDEN");
    put("nope", keyBody(key, ADMIN_PASSWORD, false)).expectError(404, "NOT_FOUND");

    Resp tooShort =
        put(id, keyBody("sk-short", ADMIN_PASSWORD, false)).expectError(400, "VALIDATION_FAILED");
    assertThat(tooShort.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactly("apiKey");
    put(id, keyBody("sk-has space-0123456789abcdef", ADMIN_PASSWORD, false))
        .expectError(400, "VALIDATION_FAILED");
    put(id, keyBody(null, ADMIN_PASSWORD, false)).expectError(400, "VALIDATION_FAILED");
    assertThat(row(id).get("keySource").asString()).isEqualTo("NONE");
    assertThat(output.getAll()).doesNotContain(key).doesNotContain("sk-has space");
  }

  @Test
  void verify는_각_사_모델_목록_조회로_확인하고_실패하면_400(CapturedOutput output) {
    // OpenAI: GET {baseUrl}/models, Bearer
    String openai = provider("OPENAI", "MISSING_TEST_API_KEY");
    String bad = newKey("sk-invalid-");
    SERVER.respond(
        401,
        "{\"error\":{\"message\":\"Incorrect API key provided: "
            + bad.substring(0, 12)
            + "****"
            + bad.substring(bad.length() - 4)
            + "\"}}");
    Resp invalid =
        put(openai, keyBody(bad, ADMIN_PASSWORD, null)).expectError(400, "API_KEY_INVALID");
    // 응답에는 사유 코드만 (공급자 응답 원문·키 조각은 로그에만)
    assertThat(invalid.body().get("params").get("reason").asString()).isEqualTo("API_401");
    assertThat(invalid.body().toString())
        .doesNotContain(bad.substring(0, 12))
        .doesNotContain("Incorrect API key");
    assertThat(SERVER.last().method()).isEqualTo("GET");
    assertThat(SERVER.last().path()).isEqualTo("/v1/models");
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer " + bad);
    assertThat(row(openai).get("keySource").asString()).isEqualTo("NONE"); // 저장하지 않았다

    SERVER.respond(500, "{\"error\":{\"message\":\"echo " + bad + "\"}}");
    Resp echoed =
        put(openai, keyBody(bad, ADMIN_PASSWORD, true)).expectError(400, "API_KEY_INVALID");
    assertThat(echoed.body().toString()).doesNotContain(bad);

    SERVER.respond(200, "{\"object\":\"list\",\"data\":[]}");
    String good = newKey("sk-good-");
    put(openai, keyBody(good, ADMIN_PASSWORD, true)).expect(200);
    assertThat(row(openai).get("keySource").asString()).isEqualTo("DB");

    // Gemini: GET {baseUrl}/v1beta/models?pageSize=1, x-goog-api-key
    String gemini = provider("GEMINI", "MISSING_TEST_API_KEY");
    String gkey = newKey("AIza-gemini-");
    SERVER.respond(200, "{\"models\":[]}");
    put(gemini, keyBody(gkey, ADMIN_PASSWORD, true)).expect(200);
    assertThat(SERVER.last().path()).isEqualTo("/v1beta/models");
    assertThat(SERVER.last().query()).isEqualTo("pageSize=1");
    assertThat(SERVER.last().header("x-goog-api-key")).isEqualTo(gkey);

    // Claude: SDK models().list() = GET /v1/models?limit=1, x-api-key
    String claude = provider("CLAUDE", "MISSING_TEST_API_KEY");
    String ckey = newKey("sk-ant-claude-");
    SERVER.respond(200, "{\"data\":[],\"has_more\":false,\"first_id\":null,\"last_id\":null}");
    put(claude, keyBody(ckey, ADMIN_PASSWORD, true)).expect(200);
    assertThat(SERVER.last().path()).isEqualTo("/v1/models");
    assertThat(SERVER.last().query()).contains("limit=1");
    assertThat(SERVER.last().header("x-api-key")).isEqualTo(ckey);
    assertThat(SERVER.last().header("anthropic-version")).isNotBlank();
    SERVER.respond(
        403, "{\"type\":\"error\",\"error\":{\"type\":\"permission_error\",\"message\":\"nope\"}}");
    put(claude, keyBody(newKey("sk-ant-denied-"), ADMIN_PASSWORD, true))
        .expectError(400, "API_KEY_INVALID");

    String logs = output.getAll();
    assertThat(logs)
        .doesNotContain(bad)
        .doesNotContain(good)
        .doesNotContain(gkey)
        .doesNotContain(ckey);
  }

  @Test
  void 모델_목록_캐시는_웹_키를_넣거나_지우면_비우고_새_키로_다시_받는다(CapturedOutput output) {
    String id = provider("OPENAI", "TEST_CRUD_API_KEY");
    String models = "/api/v1/admin/ai/providers/" + id + "/models";
    SERVER.respond(200, "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-5.1\",\"created\":1}]}");

    assertThat(api().get(models, admin).expect(200).body().get("source").asString())
        .isEqualTo("LIVE");
    api().get(models, admin).expect(200);
    assertThat(SERVER.requests()).hasSize(1); // 캐시
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer test-crud-key-value");

    // 웹 키를 넣으면 캐시를 비우고 새 키로 다시 받는다
    String web = newKey("sk-models-");
    put(id, keyBody(web, ADMIN_PASSWORD, false)).expect(200);
    api().get(models, admin).expect(200);
    assertThat(SERVER.requests()).hasSize(2);
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer " + web);
    api().get(models, admin).expect(200);
    assertThat(SERVER.requests()).hasSize(2);

    // 웹 키를 지우면 서버 설정 키로 다시 받는다
    delete(id, Map.of("currentPassword", ADMIN_PASSWORD)).expect(200);
    api().get(models, admin).expect(200);
    assertThat(SERVER.requests()).hasSize(3);
    assertThat(SERVER.last().header("Authorization")).isEqualTo("Bearer test-crud-key-value");

    // 모델 목록 조회는 변경 이력을 남기지 않는다 (키 넣기·지우기만)
    List<String> actions = new ArrayList<>();
    api()
        .get("/api/v1/admin/audit-logs?targetType=AI_PROVIDER&size=100", admin)
        .body()
        .get("items")
        .forEach(
            a -> {
              if (a.get("targetId").asString().equals(id)) {
                actions.add(a.get("action").asString());
              }
            });
    assertThat(actions)
        .containsExactly("AI_PROVIDER_KEY_DELETE", "AI_PROVIDER_KEY_SET", "AI_PROVIDER_CREATE");
    String body = api().get(models, admin).body().toString();
    assertThat(body).doesNotContain(web).doesNotContain("test-crud-key-value");
    assertThat(output.getAll()).doesNotContain(web);
  }

  @Test
  void 공급자를_지우면_웹_키_행도_지워진다() {
    String id = provider("OPENAI", "MISSING_TEST_API_KEY");
    put(id, keyBody(newKey("sk-cascade-"), ADMIN_PASSWORD, false)).expect(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tb_ai_provider_secret where provider_id = ?",
                Integer.class,
                id))
        .isOne();
    api().delete("/api/v1/admin/ai/providers/" + id, admin).expect(204);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from tb_ai_provider_secret where provider_id = ?",
                Integer.class,
                id))
        .isZero();
    // 같은 ID로 다시 만들면 옛 키가 캐시에서 살아나지 않는다
    Map<String, Object> body = new HashMap<>();
    body.put("providerId", id);
    body.put("type", "OPENAI");
    body.put("displayName", id);
    body.put("apiKeyEnv", "MISSING_TEST_API_KEY");
    assertThat(
            api()
                .post("/api/v1/admin/ai/providers", admin, body)
                .expect(201)
                .body()
                .get("keySource")
                .asString())
        .isEqualTo("NONE");
  }

  static byte[] photo() {
    byte[] b = new byte[64];
    new Random(5).nextBytes(b);
    b[0] = (byte) 0xFF;
    b[1] = (byte) 0xD8;
    b[2] = (byte) 0xFF;
    return b;
  }
}
