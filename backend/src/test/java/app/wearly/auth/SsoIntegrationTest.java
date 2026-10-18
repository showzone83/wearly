package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeOidcServer;
import app.wearly.support.FakeOidcServer.Account;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 관리자 사이트 SSO 전체 흐름 (22 문서 4.2). 가짜 OIDC IdP(FakeOidcServer: discovery·JWKS·token)로 시작 → 콜백 → 교환을
 * 실제 HTTP로 돈다. 관리자 2단계 인증을 필수로 켜서, IdP MFA를 믿는 SSO 세션은 앱 TOTP 없이 관리자 API를 쓰는지도 본다.
 */
class SsoIntegrationTest extends IntegrationTestBase {

  static final FakeOidcServer IDP = new FakeOidcServer();
  static final String SITE = "http://localhost:5173/sso/callback";
  static final String SITE_LOGIN = "http://localhost:5173/login";
  static final String P = "/api/v1/auth/sso/test";

  @DynamicPropertySource
  static void sso(DynamicPropertyRegistry r) {
    r.add("wearly.admin.mfa.required", () -> "true");
    r.add("wearly.sso.enabled", () -> "true");
    r.add("wearly.sso.providers.test.display-name", () -> "시험 회사 계정");
    r.add("wearly.sso.providers.test.issuer-uri", IDP::issuer);
    r.add("wearly.sso.providers.test.client-id", () -> FakeOidcServer.CLIENT_ID);
    r.add("wearly.sso.providers.test.client-secret", () -> FakeOidcServer.CLIENT_SECRET);
    r.add("wearly.sso.providers.test.admin-groups[0]", () -> "wearly-admins");
    r.add("wearly.sso.providers.test.redirect-allowlist[0]", () -> SITE);
    r.add("wearly.sso.providers.test.redirect-allowlist[1]", () -> SITE_LOGIN);
    // 설정이 모자란 공급자는 빠진다 (client-secret 없음)
    r.add("wearly.sso.providers.broken.issuer-uri", IDP::issuer);
    r.add("wearly.sso.providers.broken.client-id", () -> "x");
  }

  @AfterAll
  static void stop() {
    IDP.close();
  }

  @Autowired JdbcTemplate jdbc;

  /** 시작 응답: IdP authorize 주소와 state 쿠키 */
  record Started(String authorizeUrl, String state, String cookie) {}

  /** 콜백이 보낸 관리자 사이트 주소의 쿼리 */
  record Back(Map<String, String> query) {
    String get(String k) {
      return query.get(k);
    }
  }

  Started start() {
    Resp r = api().get(P + "/start?redirect=" + SITE, null).expect(302);
    String location = r.headers().getFirst(HttpHeaders.LOCATION);
    assertThat(location).startsWith(IDP.issuer() + "/auth?");
    assertThat(r.headers().getCacheControl()).contains("no-store");
    String setCookie = r.headers().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .startsWith("wearly_sso_state=")
        .contains("HttpOnly")
        .contains("SameSite=Lax")
        .contains("Path=/api/v1/auth/sso/");
    String state = FakeOidcServer.query(URI.create(location).getRawQuery()).get("state");
    return new Started(location, state, "wearly_sso_state=" + state);
  }

  Back callback(Started s, String code, String cookie) {
    HttpHeaders h = new HttpHeaders();
    if (cookie != null) {
      h.set(HttpHeaders.COOKIE, cookie);
    }
    h.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/130");
    String path = P + "/callback?state=" + s.state() + (code == null ? "" : "&code=" + code);
    Resp r = api().exchange(HttpMethod.GET, path, null, null, h).expect(302);
    String location = r.headers().getFirst(HttpHeaders.LOCATION);
    assertThat(location).startsWith(SITE + "?");
    // 콜백은 state 쿠키를 지운다
    assertThat(r.headers().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    return new Back(FakeOidcServer.query(URI.create(location).getRawQuery()));
  }

  /** 시작 → IdP 로그인 → 콜백 */
  Back login(Account a) {
    Started s = start();
    return callback(s, IDP.authorize(s.authorizeUrl(), a), s.cookie());
  }

  Resp exchange(String code) {
    HttpHeaders h = new HttpHeaders();
    h.set("X-Wearly-Client", "admin");
    return api()
        .exchange(
            HttpMethod.POST, P.replace("/test", "") + "/exchange", null, Map.of("code", code), h);
  }

  static Account staff() {
    return new Account()
        .email("sso-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.test");
  }

  long userIdByEmail(String email) {
    return jdbc.queryForObject(
        "SELECT user_id FROM tb_user WHERE email = ? ORDER BY user_id DESC LIMIT 1",
        Long.class,
        email);
  }

  String role(long userId) {
    return jdbc.queryForObject("SELECT role FROM tb_user WHERE user_id = ?", String.class, userId);
  }

  List<String> audit(long userId) {
    return jdbc.queryForList(
        "SELECT action FROM tb_admin_audit_log WHERE target_type = 'USER' AND target_id = ?"
            + " AND admin_user_id IS NULL ORDER BY audit_id",
        String.class,
        Long.toString(userId));
  }

  // ── 시험 ──

  @Test
  void 공급자_목록은_켜진_공급자만_보여_준다() {
    Resp r = api().get("/api/v1/auth/sso/providers", null).expect(200);
    assertThat(r.body().path("enabled").asBoolean()).isTrue();
    assertThat(r.body().path("providers")).hasSize(1);
    assertThat(r.body().path("providers").get(0).path("id").asString()).isEqualTo("test");
    assertThat(r.body().path("providers").get(0).path("displayName").asString())
        .isEqualTo("시험 회사 계정");
    // 설정이 모자란 공급자·없는 공급자는 404
    api().get("/api/v1/auth/sso/broken/start?redirect=" + SITE, null).expectError(404, "NOT_FOUND");
    api().get("/api/v1/auth/sso/nope/start?redirect=" + SITE, null).expectError(404, "NOT_FOUND");
  }

  @Test
  void 관리자_그룹이면_새_관리자를_만들고_교환_코드로_토큰을_준다() {
    Account a = staff();
    Back back = login(a);
    assertThat(back.get("error")).isNull();
    String code = back.get("code");
    assertThat(code).matches("[A-Za-z0-9_-]{43}");

    Resp tokens = exchange(code).expect(200);
    String access = tokens.body().path("accessToken").asString();
    assertThat(tokens.body().path("user").path("role").asString()).isEqualTo("ADMIN");
    assertThat(tokens.body().path("user").path("email").asString()).isEqualTo(a.email);
    assertThat(tokens.body().path("user").path("name").asString()).isEqualTo("SSO 관리자");
    // 관리자 2단계 인증이 필수여도 IdP MFA를 믿는 SSO 세션은 관리자 API를 쓴다 (앱 TOTP 없음)
    api().get("/api/v1/admin/me/mfa", access).expect(200);
    api().get("/api/v1/admin/dashboard", access).expect(200);

    long id = userIdByEmail(a.email);
    assertThat(audit(id)).containsExactly("SSO_ACCOUNT_CREATE");
    // 로그인 이력: SSO_LOGIN + 공급자 + amr, 기기는 ADMIN
    Map<String, Object> ev =
        jdbc.queryForMap(
            "SELECT type, client, sso_provider, sso_amr FROM tb_auth_event WHERE user_id = ?"
                + " ORDER BY auth_event_id DESC LIMIT 1",
            id);
    assertThat(ev)
        .containsEntry("type", "SSO_LOGIN")
        .containsEntry("client", "ADMIN")
        .containsEntry("sso_provider", "test")
        .containsEntry("sso_amr", "pwd,otp");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_user_sso WHERE user_id = ? AND subject = ?",
                Long.class,
                id,
                a.sub))
        .isEqualTo(1L);
    // 관리자 로그인 기록 API에도 SSO 공급자가 보인다
    Resp events = api().get("/api/v1/admin/auth-events?type=SSO_LOGIN", access).expect(200);
    assertThat(events.body().path("items").get(0).path("ssoProvider").asString()).isEqualTo("test");

    // 교환 코드는 한 번만 쓴다
    exchange(code).expectError(401, "SSO_EXCHANGE_INVALID");
    exchange("not-a-code").expectError(401, "SSO_EXCHANGE_INVALID");

    // 다시 로그인하면 같은 회원 (sub로 찾는다)
    Resp again = exchange(login(a).get("code")).expect(200);
    assertThat(again.body().path("user").path("id").asString()).isEqualTo(Long.toString(id));
    assertThat(audit(id)).containsExactly("SSO_ACCOUNT_CREATE");
  }

  @Test
  void 관리자_그룹이_아니면_거부하고_회원을_만들지_않는다() {
    Account a = staff().groups("wearly-viewers");
    Back back = login(a);
    assertThat(back.get("error")).isEqualTo("SSO_NOT_ADMIN");
    assertThat(back.get("status")).isEqualTo("403");
    assertThat(back.get("traceId")).isNotBlank();
    assertThat(back.get("code")).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_user WHERE email = ?", Long.class, a.email))
        .isZero();
  }

  @Test
  void 그룹에서_빠지면_다음_로그인을_거부하고_관리자를_해제하며_세션을_끊는다() {
    Account a = staff();
    String access =
        exchange(login(a).get("code")).expect(200).body().path("accessToken").asString();
    api().get("/api/v1/admin/dashboard", access).expect(200);
    final long id = userIdByEmail(a.email);

    a.groups = List.of("wearly-viewers");
    assertThat(login(a).get("error")).isEqualTo("SSO_NOT_ADMIN");

    assertThat(role(id)).isEqualTo("USER");
    // 남아 있던 세션도 끊겼다
    api().get("/api/v1/admin/dashboard", access).expectError(401, "TOKEN_INVALID");
    assertThat(audit(id)).containsExactly("SSO_ACCOUNT_CREATE", "USER_ROLE_CHANGE");
    assertThat(
            jdbc.queryForList(
                "SELECT type FROM tb_auth_event WHERE user_id = ? ORDER BY auth_event_id",
                String.class,
                id))
        .contains("SSO_LOGIN", "SSO_DENIED", "FORCED_LOGOUT");

    // 그룹에 다시 들어가면 다시 관리자로 올린다
    a.groups = List.of("wearly-admins");
    exchange(login(a).get("code")).expect(200);
    assertThat(role(id)).isEqualTo("ADMIN");
    assertThat(audit(id))
        .containsExactly("SSO_ACCOUNT_CREATE", "USER_ROLE_CHANGE", "USER_ROLE_CHANGE");
  }

  @Test
  void 검증된_이메일로_기존_관리자와_연결하고_일반_회원은_올리지_않는다() {
    // 기존 관리자 (비밀번호 계정)
    String adminEmail = "linked-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.test";
    String adminId = api().signup(adminEmail, "pass1234", "기존관리자").userId();
    jdbc.update("UPDATE tb_user SET role = 'ADMIN' WHERE user_id = ?", Long.parseLong(adminId));
    Resp linked = exchange(login(new Account().email(adminEmail)).get("code")).expect(200);
    assertThat(linked.body().path("user").path("id").asString()).isEqualTo(adminId);
    assertThat(audit(Long.parseLong(adminId))).containsExactly("SSO_ACCOUNT_LINK");

    // 같은 이메일의 일반 회원(고객)은 관리자로 올리지 않고 직원 계정을 따로 만든다
    String customerEmail =
        "customer-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.test";
    String customerId = api().signup(customerEmail, "pass1234", "고객").userId();
    Resp staff = exchange(login(new Account().email(customerEmail)).get("code")).expect(200);
    assertThat(staff.body().path("user").path("id").asString()).isNotEqualTo(customerId);
    assertThat(role(Long.parseLong(customerId))).isEqualTo("USER");
  }

  @Test
  void 처음_로그인에_검증된_이메일이_없으면_거부한다() {
    Account a = staff();
    a.emailVerified = Boolean.FALSE;
    assertThat(login(a).get("error")).isEqualTo("SSO_EMAIL_UNVERIFIED");
    Account b = staff();
    b.emailVerified = null;
    assertThat(login(b).get("error")).isEqualTo("SSO_EMAIL_UNVERIFIED");
    Account c = staff();
    c.email = null;
    assertThat(login(c).get("error")).isEqualTo("SSO_EMAIL_UNVERIFIED");
    // 문자열 "true"도 받는다 (일부 IdP)
    Account d = staff();
    d.emailVerified = "true";
    assertThat(login(d).get("code")).isNotNull();
  }

  @Test
  void state는_한_번만_쓰고_시작한_브라우저의_쿠키가_있어야_한다() {
    Account a = staff();
    Started s = start();
    String code = IDP.authorize(s.authorizeUrl(), a);
    // 쿠키 없이(다른 브라우저) → 거부, 그 state는 써 버렸다
    assertThat(callback(s, code, null).get("error")).isEqualTo("SSO_STATE_INVALID");
    assertThat(callback(s, code, s.cookie()).get("error")).isEqualTo("SSO_STATE_INVALID");

    Started s2 = start();
    String code2 = IDP.authorize(s2.authorizeUrl(), a);
    assertThat(callback(s2, code2, "wearly_sso_state=other").get("error"))
        .isEqualTo("SSO_STATE_INVALID");

    // 정상 → 같은 state 두 번째는 거부
    Started s3 = start();
    String code3 = IDP.authorize(s3.authorizeUrl(), a);
    assertThat(callback(s3, code3, s3.cookie()).get("code")).isNotNull();
    assertThat(callback(s3, code3, s3.cookie()).get("error")).isEqualTo("SSO_STATE_INVALID");
  }

  @Test
  void ID_토큰이_맞지_않으면_거부한다() {
    Account wrongNonce = staff();
    wrongNonce.tamper = b -> b.claim("nonce", "other");
    assertThat(login(wrongNonce).get("error")).isEqualTo("SSO_TOKEN_INVALID");

    Account wrongAud = staff();
    wrongAud.tamper = b -> b.audience("someone-else").claim("azp", "someone-else");
    assertThat(login(wrongAud).get("error")).isEqualTo("SSO_TOKEN_INVALID");

    Account wrongIss = staff();
    wrongIss.tamper = b -> b.issuer("https://evil.example");
    assertThat(login(wrongIss).get("error")).isEqualTo("SSO_TOKEN_INVALID");

    // 인가 코드를 다시 쓰면 IdP가 invalid_grant
    Started s = start();
    String code = IDP.authorize(s.authorizeUrl(), staff());
    callback(s, code, s.cookie());
    Started s2 = start();
    assertThat(callback(s2, code, s2.cookie()).get("error")).isEqualTo("SSO_TOKEN_INVALID");
  }

  @Test
  void IdP_오류와_연결_실패는_관리자_사이트로_돌려보낸다() {
    Started s = start();
    HttpHeaders h = new HttpHeaders();
    h.set(HttpHeaders.COOKIE, s.cookie());
    Resp r =
        api()
            .exchange(
                HttpMethod.GET,
                P + "/callback?state=" + s.state() + "&error=access_denied",
                null,
                null,
                h)
            .expect(302);
    Map<String, String> q =
        FakeOidcServer.query(URI.create(r.headers().getFirst(HttpHeaders.LOCATION)).getRawQuery());
    assertThat(q).containsEntry("error", "SSO_IDP_ERROR").containsEntry("reason", "access_denied");

    IDP.tokenStatus(503);
    try {
      assertThat(login(staff()).get("error")).isEqualTo("SSO_PROVIDER_UNAVAILABLE");
    } finally {
      IDP.tokenStatus(200);
    }
  }

  @Test
  void 인가_응답의_iss가_설정과_다르면_거부한다() {
    Started s = start();
    String code = IDP.authorize(s.authorizeUrl(), staff());
    HttpHeaders h = new HttpHeaders();
    h.set(HttpHeaders.COOKIE, s.cookie());
    String path =
        P + "/callback?state=" + s.state() + "&code=" + code + "&iss=https://evil.example";
    Resp r = api().exchange(HttpMethod.GET, path, null, null, h).expect(302);
    Map<String, String> q =
        FakeOidcServer.query(URI.create(r.headers().getFirst(HttpHeaders.LOCATION)).getRawQuery());
    assertThat(q).containsEntry("error", "SSO_TOKEN_INVALID");

    // 같은 iss면 받는다
    Started s2 = start();
    String code2 = IDP.authorize(s2.authorizeUrl(), staff());
    h.set(HttpHeaders.COOKIE, s2.cookie());
    String ok = P + "/callback?state=" + s2.state() + "&code=" + code2 + "&iss=" + IDP.issuer();
    Resp r2 = api().exchange(HttpMethod.GET, ok, null, null, h).expect(302);
    assertThat(r2.headers().getFirst(HttpHeaders.LOCATION)).contains("code=");
  }

  @Test
  void 허용_목록에_없는_주소로는_시작하지_않는다() {
    api()
        .get(P + "/start?redirect=https://evil.example/sso/callback", null)
        .expectError(400, "VALIDATION_FAILED");
    api().get(P + "/start", null).expectError(400, "VALIDATION_FAILED");
    // 쿼리를 붙인 주소도 허용 목록과 다르다
    api().get(P + "/start?redirect=" + SITE + "?x=1", null).expectError(400, "VALIDATION_FAILED");
    api()
        .get(P + "/logout?redirect=https://evil.example/", null)
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 로그아웃은_IdP_end_session으로_보낸다() {
    Resp r = api().get(P + "/logout?redirect=" + SITE_LOGIN, null).expect(302);
    String location = r.headers().getFirst(HttpHeaders.LOCATION);
    assertThat(location).startsWith(IDP.issuer() + "/logout?");
    Map<String, String> q = FakeOidcServer.query(URI.create(location).getRawQuery());
    assertThat(q)
        .containsEntry("client_id", FakeOidcServer.CLIENT_ID)
        .containsEntry("post_logout_redirect_uri", SITE_LOGIN);
  }
}
