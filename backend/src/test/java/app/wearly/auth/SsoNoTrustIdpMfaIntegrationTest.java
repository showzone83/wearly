package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeOidcServer;
import app.wearly.support.FakeOidcServer.Account;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * wearly.sso.trust-idp-mfa=false (22 문서 4.2): SSO로 들어와도 관리자 2단계 인증이 필요하면 교환 응답이 토큰 대신 MFA 대기
 * 토큰이다(비밀번호 로그인과 같은 다음 단계).
 */
class SsoNoTrustIdpMfaIntegrationTest extends IntegrationTestBase {

  static final FakeOidcServer IDP = new FakeOidcServer();
  static final String SITE = "http://localhost:5173/sso/callback";

  @DynamicPropertySource
  static void sso(DynamicPropertyRegistry r) {
    r.add("wearly.admin.mfa.required", () -> "true");
    r.add("wearly.secret-key", () -> "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    r.add("wearly.sso.enabled", () -> "true");
    r.add("wearly.sso.trust-idp-mfa", () -> "false");
    r.add("wearly.sso.providers.corp.issuer-uri", IDP::issuer);
    r.add("wearly.sso.providers.corp.client-id", () -> FakeOidcServer.CLIENT_ID);
    r.add("wearly.sso.providers.corp.client-secret", () -> FakeOidcServer.CLIENT_SECRET);
    r.add("wearly.sso.providers.corp.admin-groups[0]", () -> "wearly-admins");
    r.add("wearly.sso.providers.corp.redirect-allowlist[0]", () -> SITE);
  }

  @AfterAll
  static void stop() {
    IDP.close();
  }

  @Test
  void IdP_MFA를_믿지_않으면_앱_2단계_인증을_거친다() {
    Resp start = api().get("/api/v1/auth/sso/corp/start?redirect=" + SITE, null).expect(302);
    String authorize = start.headers().getFirst(HttpHeaders.LOCATION);
    String state = FakeOidcServer.query(URI.create(authorize).getRawQuery()).get("state");
    Account a =
        new Account().email("mfa-" + UUID.randomUUID().toString().substring(0, 8) + "@corp.test");
    String code = IDP.authorize(authorize, a);
    HttpHeaders h = new HttpHeaders();
    h.set(HttpHeaders.COOKIE, "wearly_sso_state=" + state);
    Resp cb =
        api()
            .exchange(
                HttpMethod.GET,
                "/api/v1/auth/sso/corp/callback?state=" + state + "&code=" + code,
                null,
                null,
                h)
            .expect(302);
    String exchangeCode =
        FakeOidcServer.query(URI.create(cb.headers().getFirst(HttpHeaders.LOCATION)).getRawQuery())
            .get("code");

    Resp r =
        api().post("/api/v1/auth/sso/exchange", null, Map.of("code", exchangeCode)).expect(200);
    assertThat(r.body().has("accessToken")).isFalse();
    assertThat(r.body().path("mfaToken").asString()).isNotBlank();
    assertThat(r.body().path("mfa").path("enrolled").asBoolean()).isFalse();
  }
}
