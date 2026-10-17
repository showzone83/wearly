package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.admin.adapter.in.startup.AdminBootstrap;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** 07 문서 2장·4장: 시작 시 관리자 계정, 회원 목록·정지·역할, 대시보드, 변경 이력 */
class AdminUserIntegrationTest extends IntegrationTestBase {

    @Autowired
    AdminBootstrap bootstrap;

    @Test
    void 시작_시_관리자_계정이_만들어지고_토큰에_role이_있다() {
        Session admin = admin();
        assertThat(admin.auth().get("user").get("role").asString()).isEqualTo("ADMIN");
        assertThat(admin.auth().get("user").get("email").asString()).isEqualTo(ADMIN_EMAIL);
        String payload = new String(Base64.getUrlDecoder().decode(admin.accessToken().split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"role\":\"ADMIN\"");
        assertThat(api().get("/api/v1/users/me", admin.accessToken()).body().get("role").asString()).isEqualTo("ADMIN");

        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=USER&size=100", admin.accessToken()).body();
        assertThat(audits.get("items").findValuesAsString("action")).anyMatch(a -> a.startsWith("ADMIN_BOOTSTRAP"));
    }

    @Test
    void 이미_있는_회원은_ADMIN으로_올리고_비밀번호는_그대로() {
        String email = uniqueEmail();
        Session s = api().signup(email, "pass1234", "기존회원");
        long id = bootstrap.ensureAdmin(email.toUpperCase(), "other-password-9", "무시됨");
        assertThat(id).isEqualTo(Long.parseLong(s.userId()));
        // 같은 Access 토큰으로도 바로 관리자 API를 쓸 수 있다 (권한은 매 요청 DB 역할)
        api().get("/api/v1/admin/dashboard", s.accessToken()).expect(200);
        Resp login = api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234")).expect(200);
        assertThat(login.body().get("user").get("role").asString()).isEqualTo("ADMIN");
        assertThat(login.body().get("user").get("name").asString()).isEqualTo("기존회원");
        // 정리: 다시 USER로
        api().patch("/api/v1/admin/users/" + s.userId(), admin().accessToken(), Map.of("role", "USER")).expect(200);
        api().get("/api/v1/admin/dashboard", s.accessToken()).expectError(403, "FORBIDDEN");
    }

    @Test
    void 정지하면_로그인_403_토큰_401_refresh_401() {
        String t = admin().accessToken();
        String email = uniqueEmail();
        Session s = api().signup(email, "pass1234", "정지대상");
        api().post("/api/v1/wardrobe/items", s.accessToken(), Map.of("name", "셔츠", "category", "TOP", "subType", "SHIRT",
                "color", "white", "pattern", "SOLID", "seasons", java.util.List.of(), "styles", java.util.List.of(),
                "formality", 2, "status", "OWNED")).expect(201);

        JsonNode list = api().get("/api/v1/admin/users?q=" + email.substring(0, 17), t).expect(200).body();
        assertThat(list.get("total").asLong()).isEqualTo(1);
        JsonNode row = list.get("items").get(0);
        assertThat(row.get("id").asString()).isEqualTo(s.userId());
        assertThat(row.get("itemCount").asLong()).isEqualTo(1);
        assertThat(row.get("aiToday").asLong()).isZero();
        assertThat(row.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(row.get("role").asString()).isEqualTo("USER");

        Resp suspended = api().patch("/api/v1/admin/users/" + s.userId(), t, Map.of("status", "SUSPENDED")).expect(200);
        assertThat(suspended.body().get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(suspended.body().get("itemCount").asLong()).isEqualTo(1);

        api().get("/api/v1/users/me", s.accessToken()).expectError(401, "TOKEN_INVALID");
        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234"))
                .expectError(403, "ACCOUNT_SUSPENDED");
        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "wrong1234"))
                .expectError(401, "INVALID_CREDENTIALS"); // 비밀번호가 틀리면 정지 여부를 알리지 않는다
        api().post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
                .expectError(401, "REFRESH_INVALID");
        assertThat(api().get("/api/v1/admin/users?status=SUSPENDED&size=100", t).body().get("items")
                .findValuesAsString("id")).contains(s.userId());

        api().patch("/api/v1/admin/users/" + s.userId(), t, Map.of("status", "ACTIVE")).expect(200);
        api().post("/api/v1/auth/login", null, Map.of("email", email, "password", "pass1234")).expect(200);

        JsonNode audits = api().get("/api/v1/admin/audit-logs?targetType=USER&size=100", t).body().get("items");
        JsonNode first = null;
        for (JsonNode a : audits) {
            if (a.get("targetId").asString().equals(s.userId())) {
                first = a; // 최신순이므로 마지막에 남는 것이 정지 기록
            }
        }
        assertThat(first.get("action").asString()).isEqualTo("USER_UPDATE");
        assertThat(first.get("before").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(first.get("after").get("status").asString()).isEqualTo("SUSPENDED");
    }

    @Test
    void 자기_자신은_내리거나_정지할_수_없고_잘못된_값은_400() {
        Session admin = admin();
        String t = admin.accessToken();
        api().patch("/api/v1/admin/users/" + admin.userId(), t, Map.of("role", "USER")).expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/users/" + admin.userId(), t, Map.of("status", "SUSPENDED"))
                .expectError(400, "VALIDATION_FAILED");
        Session s = signup();
        api().patch("/api/v1/admin/users/" + s.userId(), t, Map.of("status", "GONE")).expectError(400, "VALIDATION_FAILED");
        api().patch("/api/v1/admin/users/999999999", t, Map.of("status", "ACTIVE")).expectError(404, "NOT_FOUND");
        api().get("/api/v1/admin/users?size=101", t).expectError(400, "VALIDATION_FAILED");
        api().get("/api/v1/admin/users?role=ROOT", t).expectError(400, "VALIDATION_FAILED");
        assertThat(api().get("/api/v1/admin/users?role=ADMIN", t).body().get("items").findValuesAsString("email"))
                .contains(ADMIN_EMAIL);
        api().get("/api/v1/admin/audit-logs?targetType=NOPE", t).expectError(400, "VALIDATION_FAILED");
    }

    @Test
    void 대시보드_형식() {
        signup();
        JsonNode d = api().get("/api/v1/admin/dashboard", admin().accessToken()).expect(200).body();
        assertThat(d.get("users").asLong()).isPositive();
        assertThat(d.get("newUsers7d").asLong()).isPositive();
        assertThat(d.has("items")).isTrue();
        assertThat(d.get("aiToday").has("total")).isTrue();
        assertThat(d.get("aiToday").has("success")).isTrue();
        assertThat(d.get("aiToday").has("failed")).isTrue();
        assertThat(d.get("aiByProvider7d").isArray()).isTrue();
    }
}
