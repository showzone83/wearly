package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.auth.application.service.RefreshTokenService;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class AuthIntegrationTest extends IntegrationTestBase {

  @Autowired JwtEncoder jwtEncoder;

  @Autowired JdbcTemplate jdbc;

  @Test
  void 가입하면_201과_토큰_회원정보를_준다() {
    String email = uniqueEmail();
    Resp r =
        api()
            .post(
                "/api/v1/auth/signup",
                null,
                Map.of("email", email.toUpperCase(), "password", "abcd1234", "name", " 민지 "))
            .expect(201);

    assertThat(r.body().get("accessToken").asString()).contains(".");
    assertThat(r.body().get("accessTokenExpiresIn").asLong()).isEqualTo(1800);
    assertThat(r.body().get("refreshToken").asString()).hasSize(43); // 32바이트 base64url
    assertThat(r.body().get("refreshTokenExpiresIn").asLong()).isEqualTo(30L * 24 * 3600);
    assertThat(r.body().get("user").get("id").isString()).isTrue();
    assertThat(r.body().get("user").get("email").asString()).isEqualTo(email); // 소문자로 저장
    assertThat(r.body().get("user").get("name").asString()).isEqualTo("민지");
    assertThat(r.body().get("user").get("createdAt").asString()).endsWith("Z");
    assertThat(r.body().get("user").get("role").asString()).isEqualTo("USER"); // 가입으로는 관리자를 만들 수 없다
    String payload =
        new String(
            java.util.Base64.getUrlDecoder()
                .decode(r.body().get("accessToken").asString().split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(payload).contains("\"role\":\"USER\"");

    String hash =
        jdbc.queryForObject(
            "select token_hash from tb_refresh_token where user_id = ?",
            String.class,
            Long.parseLong(r.body().get("user").get("id").asString()));
    assertThat(hash).isEqualTo(RefreshTokenService.hash(r.body().get("refreshToken").asString()));
  }

  @Test
  void 같은_이메일은_대소문자가_달라도_409_EMAIL_TAKEN() {
    String email = uniqueEmail();
    api().signup(email, "abcd1234", "하나");
    api()
        .post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", email.toUpperCase(), "password", "abcd1234", "name", "둘"))
        .expectError(409, "EMAIL_TAKEN");
  }

  @Test
  void 가입_입력_검증_실패는_400과_fieldErrors() {
    Resp r =
        api()
            .post(
                "/api/v1/auth/signup",
                null,
                Map.of("email", "not-an-email", "password", "onlyletters", "name", ""))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(r.body().get("fieldErrors").findValuesAsString("field"))
        .containsExactlyInAnyOrder("email", "password", "name");

    api()
        .post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", uniqueEmail(), "password", "short1", "name", "a"))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .post(
            "/api/v1/auth/signup",
            null,
            Map.of("email", uniqueEmail(), "password", "abcd1234", "name", "가".repeat(21)))
        .expectError(400, "VALIDATION_FAILED");
    api().post("/api/v1/auth/signup", null, "{not json").expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 로그인_성공과_실패() {
    String email = uniqueEmail();
    api().signup(email, "abcd1234", "민지");

    Resp ok =
        api()
            .post(
                "/api/v1/auth/login",
                null,
                Map.of("email", " " + email.toUpperCase(), "password", "abcd1234"))
            .expect(200);
    assertThat(ok.body().get("user").get("email").asString()).isEqualTo(email);

    api()
        .post("/api/v1/auth/login", null, Map.of("email", email, "password", "wrong1234"))
        .expectError(401, "INVALID_CREDENTIALS");
    api()
        .post("/api/v1/auth/login", null, Map.of("email", uniqueEmail(), "password", "abcd1234"))
        .expectError(401, "INVALID_CREDENTIALS");
  }

  @Test
  void 토큰이_없으면_UNAUTHORIZED_잘못되면_TOKEN_INVALID() {
    api().get("/api/v1/users/me", null).expectError(401, "UNAUTHORIZED");
    api().get("/api/v1/wardrobe/items", null).expectError(401, "UNAUTHORIZED");
    api().get("/api/v1/users/me", "not-a-jwt").expectError(401, "TOKEN_INVALID");

    Session s = signup();
    String tampered = s.accessToken().substring(0, s.accessToken().length() - 3) + "abc";
    api().get("/api/v1/users/me", tampered).expectError(401, "TOKEN_INVALID");

    // 다른 키로 서명한 토큰
    String foreign =
        "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ3ZWFybHkiLCJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ."
            + "dGhpcy1pcy1ub3QtdGhlLXJpZ2h0LXNpZ25hdHVyZQ";
    api().get("/api/v1/users/me", foreign).expectError(401, "TOKEN_INVALID");

    // 발급자가 다른 토큰
    api()
        .get("/api/v1/users/me", token(s.userId(), "someone-else", Instant.now().plusSeconds(600)))
        .expectError(401, "TOKEN_INVALID");
  }

  @Test
  void 만료된_access_토큰은_TOKEN_EXPIRED() {
    Session s = signup();
    String expired = token(s.userId(), "wearly", Instant.now().minusSeconds(1));
    Resp r = api().get("/api/v1/users/me", expired).expectError(401, "TOKEN_EXPIRED");
    assertThat(r.headers().getFirst("WWW-Authenticate")).startsWith("Bearer");

    // 공개 경로는 만료된 Authorization 헤더가 같이 와도 막지 않는다
    api()
        .post("/api/v1/auth/refresh", expired, Map.of("refreshToken", s.refreshToken()))
        .expect(200);
  }

  @Test
  void refresh는_회전하고_이전_토큰을_다시_쓰면_family_전체를_폐기한다() {
    Session s = signup();

    Resp first =
        api()
            .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
            .expect(200);
    Session s1 = TestApi.session(first);
    assertThat(s1.refreshToken()).isNotEqualTo(s.refreshToken());
    assertThat(s1.userId()).isEqualTo(s.userId());
    api().get("/api/v1/users/me", s1.accessToken()).expect(200);

    Session s2 =
        TestApi.session(
            api()
                .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s1.refreshToken()))
                .expect(200));

    // 이전 토큰 기록: revoked_at + replaced_by_id
    Map<String, Object> old =
        jdbc.queryForMap(
            "select revoked_at, replaced_by_id from tb_refresh_token where token_hash = ?",
            RefreshTokenService.hash(s.refreshToken()));
    assertThat(old.get("revoked_at")).isNotNull();
    assertThat(old.get("replaced_by_id")).isNotNull();

    // 이미 회전된 토큰 재사용 → REFRESH_REUSED, 같은 family 전부 폐기
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
        .expectError(401, "REFRESH_REUSED");
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s2.refreshToken()))
        .expectError(401, "REFRESH_INVALID");
    Integer active =
        jdbc.queryForObject(
            "select count(*) from tb_refresh_token where user_id = ? and revoked_at is null",
            Integer.class,
            Long.parseLong(s.userId()));
    assertThat(active).isZero();
  }

  @Test
  void 재사용_감지는_다른_기기의_family에는_영향이_없다() {
    String email = uniqueEmail();
    Session phone = api().signup(email, "abcd1234", "민지");
    Session web =
        TestApi.session(
            api()
                .post("/api/v1/auth/login", null, Map.of("email", email, "password", "abcd1234"))
                .expect(200));

    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", phone.refreshToken()))
        .expect(200);
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", phone.refreshToken()))
        .expectError(401, "REFRESH_REUSED");

    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", web.refreshToken()))
        .expect(200);
  }

  @Test
  void 없거나_빈_refresh_토큰은_REFRESH_INVALID() {
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", "nope"))
        .expectError(401, "REFRESH_INVALID");
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", ""))
        .expectError(401, "REFRESH_INVALID");
    api().post("/api/v1/auth/refresh", null, Map.of()).expectError(401, "REFRESH_INVALID");
  }

  @Test
  void 만료된_refresh_토큰은_REFRESH_INVALID() {
    Session s = signup();
    jdbc.update(
        "update tb_refresh_token set expires_at = now() - interval '1 second' where token_hash = ?",
        RefreshTokenService.hash(s.refreshToken()));
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
        .expectError(401, "REFRESH_INVALID");
  }

  @Test
  void logout은_항상_204이고_이후_refresh는_REFRESH_INVALID() {
    Session s = signup();
    api().post("/api/v1/auth/logout", null, Map.of("refreshToken", s.refreshToken())).expect(204);
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
        .expectError(401, "REFRESH_INVALID");

    api().post("/api/v1/auth/logout", null, Map.of("refreshToken", "unknown")).expect(204);
    api().post("/api/v1/auth/logout", null, Map.of()).expect(204);
    api().post("/api/v1/auth/logout", null, Map.of("refreshToken", s.refreshToken())).expect(204);
  }

  @Test
  void 탈퇴하면_모든_데이터가_지워지고_남은_토큰은_TOKEN_INVALID() {
    String email = uniqueEmail();
    Session s = api().signup(email, "abcd1234", "민지");
    long userId = Long.parseLong(s.userId());
    api()
        .put(
            "/api/v1/users/me/profile",
            s.accessToken(),
            Map.of(
                "name",
                "민지",
                "dressCodes",
                java.util.List.of("CASUAL"),
                "moods",
                java.util.List.of(),
                "colors",
                java.util.List.of("navy"),
                "workType",
                "WEEKDAY",
                "workDays",
                java.util.List.of(1),
                "workRule",
                true,
                "offRule",
                false,
                "notify",
                Map.of("morning", true, "evening", false, "dMinus1", true),
                "onboarded",
                true))
        .expect(200);
    api()
        .post(
            "/api/v1/wardrobe/items",
            s.accessToken(),
            Map.of(
                "name",
                "셔츠",
                "category",
                "TOP",
                "subType",
                "SHIRT",
                "color",
                "white",
                "pattern",
                "SOLID",
                "seasons",
                java.util.List.of(),
                "styles",
                java.util.List.of(),
                "formality",
                2,
                "status",
                "OWNED"))
        .expect(201);
    api()
        .upload(
            "/api/v1/wardrobe/items/analyze",
            s.accessToken(),
            "image",
            new byte[] {1, 2, 3},
            "a.jpg",
            "image/jpeg")
        .expect(200);

    api().delete("/api/v1/users/me", s.accessToken()).expect(204);

    api().get("/api/v1/users/me", s.accessToken()).expectError(401, "TOKEN_INVALID");
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
        .expectError(401, "REFRESH_INVALID");
    api()
        .post("/api/v1/auth/login", null, Map.of("email", email, "password", "abcd1234"))
        .expectError(401, "INVALID_CREDENTIALS");
    for (String table :
        new String[] {
          "tb_user",
          "tb_user_local_credential",
          "tb_refresh_token",
          "tb_user_profile",
          "tb_wardrobe_item",
          "tb_ai_analysis_log"
        }) {
      Integer n =
          jdbc.queryForObject(
              "select count(*) from " + table + " where user_id = ?", Integer.class, userId);
      assertThat(n).as(table).isZero();
    }
    // 같은 이메일로 다시 가입할 수 있다
    api().signup(email, "abcd1234", "다시");
  }

  @Test
  void 내_정보_조회() {
    Session s = signup();
    Resp me = api().get("/api/v1/users/me", s.accessToken()).expect(200);
    assertThat(me.body()).isEqualTo(s.auth().get("user"));
  }

  @Test
  void CORS_Expo_웹_origin만_허용한다() {
    Resp ok = api().options("/api/v1/users/me", "http://localhost:8081", "GET");
    assertThat(ok.headers().getAccessControlAllowOrigin()).isEqualTo("http://localhost:8081");
    assertThat(
            api()
                .options("/api/v1/auth/login", "http://localhost:19006", "POST")
                .headers()
                .getAccessControlAllowOrigin())
        .isEqualTo("http://localhost:19006");
    assertThat(
            api()
                .options("/api/v1/auth/login", "http://127.0.0.1:8081", "POST")
                .headers()
                .getAccessControlAllowOrigin())
        .isEqualTo("http://127.0.0.1:8081");

    // 관리자 사이트 개발 서버
    assertThat(
            api()
                .options("/api/v1/admin/dashboard", "http://localhost:5173", "GET")
                .headers()
                .getAccessControlAllowOrigin())
        .isEqualTo("http://localhost:5173");

    Resp denied = api().options("/api/v1/users/me", "http://evil.example", "GET");
    assertThat(denied.status()).isEqualTo(403);
    assertThat(denied.headers().getAccessControlAllowOrigin()).isNull();
  }

  @Test
  void health는_공개되고_없는_경로는_인증_후_404() {
    api().get("/actuator/health", null).expect(200);
    Session s = signup();
    api().get("/api/v1/nothing-here", s.accessToken()).expectError(404, "NOT_FOUND");
  }

  private String token(String userId, String issuer, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(userId)
            .issuedAt(expiresAt.minusSeconds(1800))
            .expiresAt(expiresAt)
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
