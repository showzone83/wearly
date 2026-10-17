package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.auth.JwtTokenService;
import app.wearly.config.WearlyProperties;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import app.wearly.support.TestItems;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 관리자 역할·회원 정지·대시보드·AI 공급자 CRUD·AI 기록·변경 이력 (doc/07_관리자_사이트.md 2·4장) */
class AdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    AdminBootstrap bootstrap;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    app.wearly.user.UserRepository users;

    @Autowired
    app.wearly.auth.LocalCredentialRepository credentials;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    AuditService audit;

    @Test
    void 시작할_때_WEARLY_ADMIN_계정이_만들어지고_관리자_로그인이_된다() {
        Resp login = api().post("/api/v1/auth/login", null,
                Map.of("email", BOOT_ADMIN_EMAIL, "password", BOOT_ADMIN_PASSWORD)).expect(200);
        assertThat(login.body().get("user").get("role").asString()).isEqualTo("ADMIN");
        assertThat(claims(login.body().get("accessToken").asString()).get(JwtTokenService.ROLE_CLAIM).asString())
                .isEqualTo("ADMIN");
        String token = login.body().get("accessToken").asString();
        api().get("/api/v1/admin/dashboard", token).expect(200);
        // 가입 API로는 관리자를 만들 수 없다
        Session s = signup();
        assertThat(s.auth().get("user").get("role").asString()).isEqualTo("USER");
        assertThat(claims(s.accessToken()).get(JwtTokenService.ROLE_CLAIM).asString()).isEqualTo("USER");
        Long count = jdbcTemplate.queryForObject("select count(*) from tb_admin_audit_log where action like 'ADMIN_BOOTSTRAP_%'",
                Long.class);
        assertThat(count).isPositive();
    }

    @Test
    void 이미_있는_계정은_관리자로_올리고_정지도_푼다() {
        String email = uniqueEmail();
        Session s = api().signup(email, "pass1234", "올릴사람");
        jdbcTemplate.update("update tb_user set status = 'SUSPENDED' where user_id = ?", Long.parseLong(s.userId()));
        AdminBootstrap other = new AdminBootstrap(
                new WearlyProperties(null, null, null, new WearlyProperties.Admin(email.toUpperCase(), "다른비번1", "무시")),
                users, credentials, passwordEncoder, audit);
        tx.executeWithoutResult(st -> other.run(null));
        Resp login = api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234")).expect(200);
        assertThat(login.body().get("user").get("role").asString()).isEqualTo("ADMIN");
        assertThat(login.body().get("user").get("name").asString()).isEqualTo("올릴사람"); // 이름·비밀번호는 그대로

        // 약한 비밀번호면 새 계정을 만들지 않는다
        String weak = uniqueEmail();
        AdminBootstrap weakBoot = new AdminBootstrap(
                new WearlyProperties(null, null, null, new WearlyProperties.Admin(weak, "short", null)),
                users, credentials, passwordEncoder, audit);
        tx.executeWithoutResult(st -> weakBoot.run(null));
        api().post("/api/v1/auth/login", null, Map.of("email", weak, "password", "short"))
                .expectError(401, "INVALID_CREDENTIALS");
    }

    @Test
    void 회원_정지하면_로그인_403_토큰_401_refresh_401() {
        Session admin = adminSession();
        String email = uniqueEmail();
        Session user = api().signup(email, "pass1234", "정지대상");

        Resp patched = api().patch("/api/v1/admin/users/" + user.userId(), admin.accessToken(), Map.of("status", "SUSPENDED"))
                .expect(200);
        assertThat(patched.body().get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(patched.body().get("id").asString()).isEqualTo(user.userId());

        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234"))
                .expectError(403, "ACCOUNT_SUSPENDED");
        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "wrong1234"))
                .expectError(401, "INVALID_CREDENTIALS"); // 비밀번호가 틀리면 정지 사실을 알리지 않는다
        api().get("/api/v1/users/me", user.accessToken()).expectError(401, "TOKEN_INVALID");
        api().post("/api/v1/auth/refresh", null, Map.of("refreshToken", user.refreshToken()))
                .expectError(401, "REFRESH_INVALID");

        api().patch("/api/v1/admin/users/" + user.userId(), admin.accessToken(), Map.of("status", "ACTIVE")).expect(200);
        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234")).expect(200);
        api().get("/api/v1/users/me", user.accessToken()).expect(200);

        // 역할 올리기 → 바로 관리자 API를 쓸 수 있다 (권한은 DB 역할로 판단)
        api().patch("/api/v1/admin/users/" + user.userId(), admin.accessToken(), Map.of("role", "ADMIN")).expect(200);
        api().get("/api/v1/admin/dashboard", user.accessToken()).expect(200);
        api().patch("/api/v1/admin/users/" + user.userId(), admin.accessToken(), Map.of("role", "USER")).expect(200);
        api().get("/api/v1/admin/dashboard", user.accessToken()).expectError(403, "FORBIDDEN");

        // 자기 자신은 내리거나 정지할 수 없다, 잘못된 값은 400, 없는 회원은 404
        api().patch("/api/v1/admin/users/" + admin.userId(), admin.accessToken(), Map.of("role", "USER"))
                .expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/users/" + admin.userId(), admin.accessToken(), Map.of("status", "SUSPENDED"))
                .expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/users/" + user.userId(), admin.accessToken(), Map.of("status", "GONE"))
                .expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/users/999999999", admin.accessToken(), Map.of("status", "ACTIVE"))
                .expectError(404, "NOT_FOUND");

        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=USER", admin.accessToken()).expect(200).body();
        JsonNode suspend = audits.get("items").valueStream()
                .filter(a -> a.get("targetId").asString().equals(user.userId())
                        && "SUSPENDED".equals(a.get("after").get("status").asString()))
                .findFirst().orElseThrow();
        assertThat(suspend.get("action").asString()).isEqualTo("USER_UPDATE");
        assertThat(suspend.get("before").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(suspend.get("adminEmail").asString()).isNotBlank();
        api().get("/api/v1/admin/audit-logs?targetType=NOPE", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 회원_목록_검색과_페이지() {
        Session admin = adminSession();
        String tag = "srch" + suffix().toLowerCase();
        Session a = api().signup(tag + "-a@wearly.test", "pass1234", "검색A");
        api().signup(tag + "-b@wearly.test", "pass1234", "검색B");
        api().post("/api/v1/wardrobe/items", a.accessToken(), TestItems.item("티", "TOP", "TEE", "white")).expect(201);
        api().upload("/api/v1/wardrobe/items/analyze", a.accessToken(), "image", photo(1), "a.png", "image/png").expect(200);

        JsonNode page = api().get("/api/v1/admin/users?q=" + tag + "&size=1", admin.accessToken()).expect(200).body();
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(page.get("items").size()).isEqualTo(1);
        assertThat(page.get("size").asInt()).isEqualTo(1);
        JsonNode all = api().get("/api/v1/admin/users?q=" + tag.toUpperCase(), admin.accessToken()).body();
        JsonNode rowA = all.get("items").valueStream().filter(r -> r.get("id").asString().equals(a.userId())).findFirst()
                .orElseThrow();
        assertThat(rowA.get("itemCount").asLong()).isEqualTo(1);
        assertThat(rowA.get("aiToday").asLong()).isEqualTo(1);
        assertThat(rowA.get("role").asString()).isEqualTo("USER");
        assertThat(rowA.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(rowA.get("createdAt").asString()).endsWith("Z");

        assertThat(api().get("/api/v1/admin/users?role=ADMIN&size=100", admin.accessToken()).body().get("items")
                .valueStream().allMatch(r -> r.get("role").asString().equals("ADMIN"))).isTrue();
        api().get("/api/v1/admin/users?size=101", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
        api().get("/api/v1/admin/users?page=-1", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
        api().get("/api/v1/admin/users?role=ROOT", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 대시보드와_AI_분석_기록() {
        Session admin = adminSession();
        Session s = signup();
        api().upload("/api/v1/wardrobe/items/analyze", s.accessToken(), "image", photo(2), "a.png", "image/png").expect(200);

        JsonNode d = api().get("/api/v1/admin/dashboard", admin.accessToken()).expect(200).body();
        assertThat(d.get("users").asLong()).isPositive();
        assertThat(d.get("newUsers7d").asLong()).isPositive();
        assertThat(d.has("items")).isTrue();
        assertThat(d.get("aiToday").get("total").asLong()).isPositive();
        assertThat(d.get("aiToday").get("success").asLong()).isPositive();
        assertThat(d.get("aiToday").has("failed")).isTrue();
        JsonNode mock = d.get("aiByProvider7d").valueStream().filter(p -> p.get("providerId").asString().equals("mock"))
                .findFirst().orElseThrow();
        assertThat(mock.get("success").asLong()).isPositive();
        assertThat(mock.has("avgLatencyMs")).isTrue();
        assertThat(mock.has("inputTokens")).isTrue();

        String email = s.auth().get("user").get("email").asString();
        JsonNode logs = api().get("/api/v1/admin/ai/logs?userEmail=" + email.substring(0, 12) + "&providerId=mock&status=SUCCESS",
                admin.accessToken()).expect(200).body();
        assertThat(logs.get("total").asLong()).isEqualTo(1);
        JsonNode row = logs.get("items").get(0);
        assertThat(row.get("userEmail").asString()).isEqualTo(email);
        assertThat(row.get("feature").asString()).isEqualTo("ITEM_ANALYZE");
        assertThat(row.get("providerId").asString()).isEqualTo("mock");
        assertThat(row.get("imageBytes").asInt()).isEqualTo(64);
        assertThat(row.get("resultSummary").asString()).contains("(").contains("/");
        assertThat(row.get("id").isString()).isTrue();

        JsonNode detail = api().get("/api/v1/admin/ai/logs/" + row.get("id").asString(), admin.accessToken()).expect(200).body();
        assertThat(detail.get("result").get("subType").isString()).isTrue();
        assertThat(detail.get("imageSha256").asString()).hasSize(64);
        api().get("/api/v1/admin/ai/logs/999999999", admin.accessToken()).expectError(404, "NOT_FOUND");

        String today = java.time.LocalDate.now(app.wearly.config.ClockConfig.SEOUL).toString();
        assertThat(api().get("/api/v1/admin/ai/logs?userEmail=" + email + "&from=" + today + "&to=" + today,
                admin.accessToken()).body().get("total").asLong()).isEqualTo(1);
        assertThat(api().get("/api/v1/admin/ai/logs?userEmail=" + email + "&to=2000-01-01", admin.accessToken()).body()
                .get("total").asLong()).isZero();
        api().get("/api/v1/admin/ai/logs?from=yesterday", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
        api().get("/api/v1/admin/ai/logs?status=OK", admin.accessToken()).expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void AI_공급자_CRUD와_keyConfigured() {
        Session admin = adminSession();
        String t = admin.accessToken();
        String id = "openai-" + suffix().toLowerCase();

        JsonNode types = api().get("/api/v1/admin/ai/types", t).expect(200).body();
        assertThat(types.valueStream().map(x -> x.get("type").asString()).toList())
                .containsExactly("CLAUDE", "OPENAI", "GEMINI");
        assertThat(types.get(1).get("defaultApiKeyEnv").asString()).isEqualTo("OPENAI_API_KEY");
        assertThat(types.get(1).get("options").valueStream().map(o -> o.get("key").asString()).toList())
                .contains("temperature", "maxTokens", "timeoutSeconds", "baseUrl");

        // 사용하려면 모델 필수, 키 환경변수 이름은 *_API_KEY만, 옵션 검증
        api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", id, "type", "OPENAI", "displayName", "오픈AI",
                "apiKeyEnv", "OPENAI_API_KEY", "enabled", true)).expectError(400, "VALIDATION_FAILED");
        Resp bad = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", "Bad Id", "type", "MOCK",
                "displayName", "", "apiKeyEnv", "SPRING_DATASOURCE_PASSWORD")).expectError(400, "VALIDATION_FAILED");
        assertThat(bad.body().get("fieldErrors").findValuesAsString("field"))
                .contains("providerId", "type", "displayName", "apiKeyEnv");
        Resp badOpt = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", id, "type", "OPENAI",
                "displayName", "오픈AI", "apiKeyEnv", "OPENAI_API_KEY",
                "options", Map.of("temperature", 3, "effort", "high", "baseUrl", "ftp://x"))).expectError(400, "VALIDATION_FAILED");
        assertThat(badOpt.body().get("fieldErrors").findValuesAsString("field"))
                .contains("options.temperature", "options.effort", "options.baseUrl");
        // 기본 설정(WEARLY_AI_ALLOW_BASE_URL=false)에서는 올바른 주소여도 baseUrl을 받지 않는다 (키를 다른 곳으로 못 보내게)
        Resp noBase = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", id, "type", "OPENAI",
                "displayName", "오픈AI", "apiKeyEnv", "OPENAI_API_KEY", "options", Map.of("baseUrl", "https://example.com/v1")))
                .expectError(400, "VALIDATION_FAILED");
        assertThat(noBase.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("options.baseUrl");
        assertThat(noBase.body().get("fieldErrors").get(0).get("message").asString()).contains("WEARLY_AI_ALLOW_BASE_URL");
        api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", "mock", "type", "OPENAI", "displayName", "x",
                "apiKeyEnv", "OPENAI_API_KEY")).expectError(400, "VALIDATION_FAILED");

        Resp created = api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", id, "type", "OPENAI",
                "displayName", "오픈AI", "apiKeyEnv", "WEARLY_IT_UNSET_API_KEY", "priority", 50,
                "options", Map.of("temperature", 0.2, "maxTokens", 2000))).expect(201);
        JsonNode p = created.body();
        assertThat(p.get("enabled").asBoolean()).isFalse();
        assertThat(p.get("keyConfigured").asBoolean()).isFalse();
        assertThat(p.get("usable").asBoolean()).isFalse();
        assertThat(p.get("model").isNull()).isTrue();
        assertThat(p.get("options").size()).isEqualTo(2);
        assertThat(p.get("options").get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(p.get("options").get("maxTokens").asInt()).isEqualTo(2000);
        assertThat(p.get("today").toString()).isEqualTo("{\"success\":0,\"failed\":0}");
        api().post("/api/v1/admin/ai/providers", t, Map.of("providerId", id, "type", "OPENAI", "displayName", "x",
                "apiKeyEnv", "OPENAI_API_KEY")).expectError(409, "PROVIDER_EXISTS");

        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("enabled", true)).expectError(400, "VALIDATION_FAILED");
        JsonNode on = api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("enabled", true, "model", "gpt-test"))
                .expect(200).body();
        assertThat(on.get("enabled").asBoolean()).isTrue();
        assertThat(on.get("usable").asBoolean()).isFalse(); // 키가 없다
        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("model", "")).expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("providerId", "renamed")).expectError(400, "VALIDATION_FAILED");
        // 종류를 바꾸면 기존 옵션을 새 종류로 다시 검사한다 (temperature는 CLAUDE에 없음)
        api().patch("/api/v1/admin/ai/providers/" + id, t, Map.of("type", "CLAUDE")).expectError(400, "VALIDATION_FAILED");

        // 시험 분석: 키가 없으면 호출하지 않고 KEY_MISSING
        JsonNode test = api().upload("/api/v1/admin/ai/providers/" + id + "/test", t, "image", photo(3), "a.png", "image/png")
                .expect(200).body();
        assertThat(test.get("ok").asBoolean()).isFalse();
        assertThat(test.get("errorCode").asString()).isEqualTo("KEY_MISSING");

        assertThat(api().get("/api/v1/admin/ai/providers", t).expect(200).body().valueStream()
                .anyMatch(x -> x.get("providerId").asString().equals(id))).isTrue();
        api().delete("/api/v1/admin/ai/providers/" + id, t).expect(204);
        api().delete("/api/v1/admin/ai/providers/" + id, t).expectError(404, "NOT_FOUND");

        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=AI_PROVIDER&size=100", t).body();
        assertThat(audits.get("items").valueStream().filter(a -> a.get("targetId").asString().equals(id))
                .map(a -> a.get("action").asString()).toList())
                .contains("AI_PROVIDER_CREATE", "AI_PROVIDER_UPDATE", "AI_PROVIDER_DELETE");
    }

    @Test
    void CORS_관리자_사이트_개발_주소를_허용한다() {
        Resp ok = api().options("/api/v1/admin/dashboard", "http://localhost:5173", "GET");
        assertThat(ok.headers().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        Resp codes = api().options("/api/v1/codes", "http://localhost:5173", "GET");
        assertThat(codes.headers().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
    }

    private static byte[] photo(long seed) {
        byte[] b = new byte[64];
        new Random(seed).nextBytes(b);
        b[0] = (byte) 0x89;
        b[1] = 'P';
        b[2] = 'N';
        b[3] = 'G';
        return b;
    }

    private static JsonNode claims(String jwt) {
        String payload = jwt.split("\\.")[1];
        return TestApi.JSON.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}
