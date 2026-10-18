package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestTotp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 2단계 인증 TOTP 전체 흐름 (22 문서 4.1). 설정 wearly.admin.mfa.required=true와 마스터 키를 켠다. 관리자는 가입한 회원을
 * DB에서 올려 만든다 (시작 때 만든 관리자는 다른 테스트가 비밀번호만으로 쓰므로 등록하지 않는다).
 */
class AdminMfaIntegrationTest extends IntegrationTestBase {

  static final String MASTER_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
  static final String PASSWORD = "pass1234";

  @DynamicPropertySource
  static void mfaOn(DynamicPropertyRegistry registry) {
    registry.add("wearly.admin.mfa.required", () -> "true");
    registry.add("wearly.secret-key", () -> MASTER_KEY);
  }

  @Autowired JdbcTemplate jdbc;

  /** 등록을 마친 관리자. step = 등록 확인에 쓴 단계 (다음 TOTP는 step + 1) */
  record Admin(
      String email,
      String id,
      String secret,
      long step,
      String accessToken,
      String refreshToken,
      List<String> recoveryCodes) {}

  static HttpHeaders site() {
    HttpHeaders h = new HttpHeaders();
    h.set("X-Wearly-Client", "admin");
    h.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/130");
    return h;
  }

  Resp siteLogin(String email, String password) {
    return api()
        .exchange(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("email", email, "password", password),
            site());
  }

  Resp verify(String mfaToken, String field, String value) {
    Map<String, Object> body = new HashMap<>();
    body.put("mfaToken", mfaToken);
    body.put(field, value);
    return api().exchange(HttpMethod.POST, "/api/v1/auth/mfa/verify", null, body, site());
  }

  /** 가입한 회원을 DB에서 관리자로 올린다 */
  String newAdminUser(String email) {
    String id = api().signup(email, PASSWORD, "관리자후보").userId();
    jdbc.update("UPDATE tb_user SET role = 'ADMIN' WHERE user_id = ?", Long.parseLong(id));
    return id;
  }

  /** 관리자 사이트 로그인 → 등록 시작 → 코드 확인까지 */
  Admin enrolledAdmin() {
    String email = uniqueEmail();
    String id = newAdminUser(email);
    String token = siteLogin(email, PASSWORD).expect(200).body().get("mfaToken").asString();
    JsonNode setup =
        api().post("/api/v1/auth/mfa/setup", null, Map.of("mfaToken", token)).expect(200).body();
    String secret = setup.get("secret").asString();
    long step = TestTotp.step(Instant.now());
    JsonNode done = verify(token, "code", TestTotp.code(secret, step)).expect(200).body();
    List<String> codes = new ArrayList<>();
    done.get("recoveryCodes").forEach(c -> codes.add(c.asString()));
    return new Admin(
        email,
        id,
        secret,
        step,
        done.get("accessToken").asString(),
        done.get("refreshToken").asString(),
        codes);
  }

  @Test
  void 관리자_사이트_로그인은_대기_토큰을_주고_등록을_마쳐야_관리자_API를_쓴다() {
    String email = uniqueEmail();
    String id = newAdminUser(email);

    Resp login = siteLogin(email, PASSWORD).expect(200);
    JsonNode b = login.body();
    assertThat(b.get("mfa").get("required").asBoolean()).isTrue();
    assertThat(b.get("mfa").get("enrolled").asBoolean()).isFalse();
    assertThat(b.get("mfaTokenExpiresIn").asLong()).isEqualTo(300);
    assertThat(b.has("accessToken")).isFalse();
    assertThat(b.has("refreshToken")).isFalse();
    String token = b.get("mfaToken").asString();

    // 대기 토큰은 관리자 API에 쓸 수 없다 (JWT가 아니다)
    api().get("/api/v1/admin/dashboard", token).expectError(401, "TOKEN_INVALID");
    // 앱 로그인은 토큰을 주지만 그 세션으로는 관리자 API를 못 쓴다 (앱 API는 그대로)
    Resp app =
        api()
            .post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
            .expect(200);
    String appToken = app.body().get("accessToken").asString();
    api().get("/api/v1/admin/dashboard", appToken).expectError(403, "MFA_REQUIRED");
    api().get("/api/v1/users/me", appToken).expect(200);

    // 등록 시작 전에 코드를 보내면 409
    verify(token, "code", "123456").expectError(409, "MFA_SETUP_REQUIRED");
    JsonNode setup =
        api().post("/api/v1/auth/mfa/setup", null, Map.of("mfaToken", token)).expect(200).body();
    String secret = setup.get("secret").asString();
    assertThat(secret).matches("[A-Z2-7]{32}");
    assertThat(setup.get("otpauthUrl").asString())
        .startsWith("otpauth://totp/Wearly:")
        .contains("secret=" + secret)
        .contains("issuer=Wearly");
    verify(token, "code", "12ab").expectError(400, "VALIDATION_FAILED");

    long step = TestTotp.step(Instant.now());
    JsonNode done = verify(token, "code", TestTotp.code(secret, step)).expect(200).body();
    assertThat(done.get("user").get("role").asString()).isEqualTo("ADMIN");
    assertThat(done.get("recoveryCodes")).hasSize(10);
    assertThat(done.get("recoveryCodesLeft").asInt()).isEqualTo(10);
    String access = done.get("accessToken").asString();
    api().get("/api/v1/admin/dashboard", access).expect(200);
    // 대기 토큰은 한 번만 쓴다
    verify(token, "code", TestTotp.code(secret, step + 1)).expectError(401, "MFA_TOKEN_INVALID");

    // DB에는 비밀을 암호문으로만 둔다
    byte[] cipher =
        jdbc.queryForObject(
            "SELECT secret_cipher FROM tb_user_mfa WHERE user_id = ?",
            byte[].class,
            Long.parseLong(id));
    assertThat(new String(cipher, StandardCharsets.ISO_8859_1)).doesNotContain(secret);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_user_mfa_recovery_code WHERE user_id = ?",
                Long.class,
                Long.parseLong(id)))
        .isEqualTo(10);

    // 다음 로그인: 등록됨. 같은 코드(같은 단계)는 다시 못 쓴다
    String next = siteLogin(email, PASSWORD).expect(200).body().get("mfaToken").asString();
    assertThat(siteLogin(email, PASSWORD).body().get("mfa").get("enrolled").asBoolean()).isTrue();
    Resp reused =
        verify(next, "code", TestTotp.code(secret, step)).expectError(400, "MFA_CODE_INVALID");
    assertThat(reused.body().get("params").get("remainingAttempts").asInt()).isEqualTo(4);
    api()
        .post("/api/v1/auth/mfa/setup", null, Map.of("mfaToken", next))
        .expectError(409, "MFA_ALREADY_ENROLLED");
    String recovery = done.get("recoveryCodes").get(0).asString();
    JsonNode viaRecovery = verify(next, "recoveryCode", recovery).expect(200).body();
    assertThat(viaRecovery.get("recoveryCodes").isNull()).isTrue();
    assertThat(viaRecovery.get("recoveryCodesLeft").asInt()).isEqualTo(9);
    // 쓴 복구 코드는 다시 못 쓴다
    String again = siteLogin(email, PASSWORD).expect(200).body().get("mfaToken").asString();
    verify(again, "recoveryCode", recovery).expectError(400, "MFA_CODE_INVALID");

    // 등록한 관리자의 예전 앱 세션도 관리자 API를 못 쓴다
    api().get("/api/v1/admin/dashboard", appToken).expectError(403, "MFA_REQUIRED");

    // 로그인 기록: 이 관리자의 MFA 성공·실패
    JsonNode events =
        api()
            .get("/api/v1/admin/auth-events?userId=" + id + "&size=100", access)
            .expect(200)
            .body();
    List<String> types = new ArrayList<>();
    events.get("items").forEach(e -> types.add(e.get("type").asString()));
    assertThat(types).contains("LOGIN", "MFA_SUCCESS", "MFA_FAILED");
    JsonNode failed =
        api()
            .get("/api/v1/admin/auth-events?type=MFA_FAILED&userId=" + id, access)
            .expect(200)
            .body();
    assertThat(failed.get("total").asLong()).isEqualTo(2);
    JsonNode row = failed.get("items").get(0);
    assertThat(row.get("userId").asString()).isEqualTo(id);
    assertThat(row.get("email").asString()).isEqualTo(email);
    assertThat(row.get("role").asString()).isEqualTo("ADMIN");
    assertThat(row.get("client").asString()).isEqualTo("ADMIN");
    api().get("/api/v1/admin/auth-events?type=NOPE", access).expectError(400, "VALIDATION_FAILED");
    api()
        .get("/api/v1/admin/auth-events?from=yesterday", access)
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 다섯_번_틀리면_잠기고_대기_토큰이_무효가_된다() {
    Admin a = enrolledAdmin();
    String token = siteLogin(a.email(), PASSWORD).expect(200).body().get("mfaToken").asString();
    for (int i = 4; i >= 1; i--) {
      Resp r = verify(token, "code", "000000").expectError(400, "MFA_CODE_INVALID");
      assertThat(r.body().get("params").get("remainingAttempts").asInt()).isEqualTo(i);
    }
    Resp locked = verify(token, "code", "000000").expectError(429, "MFA_LOCKED");
    assertThat(locked.body().get("params").get("retryAfterSeconds").asLong()).isBetween(1L, 900L);
    verify(token, "code", TestTotp.code(a.secret(), a.step() + 1))
        .expectError(401, "MFA_TOKEN_INVALID");
    // 잠긴 동안은 비밀번호가 맞아도 429, 틀리면 그대로 401 (잠금 여부를 알리지 않는다)
    siteLogin(a.email(), PASSWORD).expectError(429, "MFA_LOCKED");
    siteLogin(a.email(), "wrong-pass1").expectError(401, "INVALID_CREDENTIALS");

    JsonNode events =
        api()
            .get("/api/v1/admin/auth-events?userId=" + a.id() + "&size=100", a.accessToken())
            .expect(200)
            .body();
    List<String> types = new ArrayList<>();
    events.get("items").forEach(e -> types.add(e.get("type").asString()));
    assertThat(types).contains("MFA_LOCKED", "LOGIN_FAILED");
    assertThat(types.stream().filter("MFA_FAILED"::equals)).hasSize(5);
  }

  @Test
  void 일반_회원_로그인_응답은_관리자_사이트_헤더여도_그대로다() {
    String email = uniqueEmail();
    api().signup(email, PASSWORD, "일반");
    JsonNode b = siteLogin(email, PASSWORD).expect(200).body();
    List<String> keys = new ArrayList<>();
    b.propertyNames().forEach(keys::add);
    assertThat(keys)
        .containsExactlyInAnyOrder(
            "accessToken", "accessTokenExpiresIn", "refreshToken", "refreshTokenExpiresIn", "user");
    api()
        .get("/api/v1/admin/dashboard", b.get("accessToken").asString())
        .expectError(403, "FORBIDDEN");
  }

  @Test
  void 비밀번호를_바꾸면_다른_세션은_끊기고_지금_세션은_남는다() {
    Admin a = enrolledAdmin();
    String other =
        api()
            .post("/api/v1/auth/login", null, Map.of("email", a.email(), "password", PASSWORD))
            .expect(200)
            .body()
            .get("accessToken")
            .asString();

    api()
        .post(
            "/api/v1/admin/me/password",
            a.accessToken(),
            Map.of("currentPassword", "wrong1234", "newPassword", "newpass99"))
        .expectError(403, "REAUTH_FAILED");
    Resp bad =
        api()
            .post(
                "/api/v1/admin/me/password",
                a.accessToken(),
                Map.of("currentPassword", PASSWORD, "newPassword", "short"))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(bad.body().get("fieldErrors").get(0).get("field").asString())
        .isEqualTo("newPassword");
    api()
        .post(
            "/api/v1/admin/me/password",
            a.accessToken(),
            Map.of("currentPassword", PASSWORD, "newPassword", "newpass99"))
        .expect(204);

    api().get("/api/v1/users/me", other).expectError(401, "TOKEN_INVALID");
    api().get("/api/v1/admin/dashboard", a.accessToken()).expect(200);
    siteLogin(a.email(), PASSWORD).expectError(401, "INVALID_CREDENTIALS");
    siteLogin(a.email(), "newpass99").expect(200);

    JsonNode audits =
        api()
            .get("/api/v1/admin/audit-logs?targetType=USER&size=100", a.accessToken())
            .body()
            .get("items");
    boolean found = false;
    for (JsonNode x : audits) {
      if (x.get("targetId").asString().equals(a.id())
          && x.get("action").asString().equals("ADMIN_PASSWORD_CHANGE")) {
        found = true;
        // 가입 때 세션 + 앱 로그인 세션 (지금 세션은 남는다)
        assertThat(x.get("after").get("endedSessions").asInt()).isEqualTo(2);
      }
    }
    assertThat(found).isTrue();
  }

  @Test
  void 내_2단계_인증을_초기화하고_다시_등록한다() {
    Admin a = enrolledAdmin();
    String t = a.accessToken();
    JsonNode status = api().get("/api/v1/admin/me/mfa", t).expect(200).body();
    assertThat(status.get("enrolled").asBoolean()).isTrue();
    assertThat(status.get("recoveryCodesLeft").asInt()).isEqualTo(10);
    assertThat(status.get("required").asBoolean()).isTrue();
    assertThat(status.get("enrolledAt").asString()).isNotBlank();
    api().post("/api/v1/admin/me/mfa/setup", t, Map.of()).expectError(409, "MFA_ALREADY_ENROLLED");

    api()
        .post(
            "/api/v1/admin/me/mfa/reset",
            t,
            Map.of("currentPassword", "wrong1234", "recoveryCode", a.recoveryCodes().get(0)))
        .expectError(403, "REAUTH_FAILED");
    api()
        .post(
            "/api/v1/admin/me/mfa/reset",
            t,
            Map.of("currentPassword", PASSWORD, "recoveryCode", a.recoveryCodes().get(0)))
        .expect(204);
    assertThat(api().get("/api/v1/admin/me/mfa", t).body().get("enrolled").asBoolean()).isFalse();
    // 지금 세션은 2단계 인증을 확인한 세션이라 그대로 쓴다 → 바로 다시 등록
    JsonNode setup = api().post("/api/v1/admin/me/mfa/setup", t, Map.of()).expect(200).body();
    String secret = setup.get("secret").asString();
    assertThat(secret).isNotEqualTo(a.secret());
    api()
        .post("/api/v1/admin/me/mfa/enable", t, Map.of("code", "000000"))
        .expectError(400, "MFA_CODE_INVALID");
    JsonNode enabled =
        api()
            .post(
                "/api/v1/admin/me/mfa/enable",
                t,
                Map.of("code", TestTotp.code(secret, TestTotp.step(Instant.now()))))
            .expect(200)
            .body();
    assertThat(enabled.get("recoveryCodes")).hasSize(10);
    assertThat(api().get("/api/v1/admin/me/mfa", t).body().get("enrolled").asBoolean()).isTrue();
    api()
        .post("/api/v1/admin/me/mfa/reset", t, Map.of("currentPassword", PASSWORD))
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 다른_관리자의_2단계_인증을_초기화하면_그_관리자의_세션이_끊긴다() {
    Admin a = enrolledAdmin();
    Admin b = enrolledAdmin();

    api()
        .delete("/api/v1/admin/users/" + a.id() + "/mfa", a.accessToken())
        .expectError(400, "VALIDATION_FAILED");
    JsonNode row =
        api().get("/api/v1/admin/users?q=" + b.email(), a.accessToken()).body().get("items").get(0);
    assertThat(row.get("mfaEnrolled").asBoolean()).isTrue();

    api().delete("/api/v1/admin/users/" + b.id() + "/mfa", a.accessToken()).expect(204);
    api().get("/api/v1/admin/dashboard", b.accessToken()).expectError(401, "TOKEN_INVALID");
    api()
        .delete("/api/v1/admin/users/999999999/mfa", a.accessToken())
        .expectError(404, "NOT_FOUND");
    // 다음 로그인에서 다시 등록한다
    JsonNode login = siteLogin(b.email(), PASSWORD).expect(200).body();
    assertThat(login.get("mfa").get("enrolled").asBoolean()).isFalse();
    assertThat(
            api()
                .get("/api/v1/admin/users?q=" + b.email(), a.accessToken())
                .body()
                .get("items")
                .get(0)
                .get("mfaEnrolled")
                .asBoolean())
        .isFalse();
  }

  @Test
  void 관리자를_해제하면_권한이_바로_빠진다() {
    Admin a = enrolledAdmin();
    Admin b = enrolledAdmin();
    api()
        .patch("/api/v1/admin/users/" + b.id(), a.accessToken(), Map.of("role", "USER"))
        .expect(200);
    api().get("/api/v1/admin/dashboard", b.accessToken()).expectError(401, "TOKEN_INVALID");
    api()
        .patch("/api/v1/admin/users/" + a.id(), a.accessToken(), Map.of("role", "USER"))
        .expectError(400, "VALIDATION_FAILED");
    // 해제된 회원은 관리자 사이트에서 대기 토큰 없이 일반 토큰을 받는다 (사이트가 NOT_ADMIN으로 거부)
    JsonNode login = siteLogin(b.email(), PASSWORD).expect(200).body();
    assertThat(login.has("mfa")).isFalse();
    assertThat(login.get("user").get("role").asString()).isEqualTo("USER");
  }
}
