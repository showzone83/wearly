package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SSO 기본값(꺼짐): 공급자 목록은 비고 시작·콜백·교환·로그아웃은 404 (22 문서 4.2) */
class SsoDisabledIntegrationTest extends IntegrationTestBase {

  @Test
  void 꺼져_있으면_목록은_비고_나머지는_404다() {
    Resp r = api().get("/api/v1/auth/sso/providers", null).expect(200);
    assertThat(r.body().path("enabled").asBoolean()).isFalse();
    assertThat(r.body().path("providers").isArray()).isTrue();
    assertThat(r.body().path("providers")).isEmpty();

    String site = "http://localhost:5173/sso/callback";
    api()
        .get("/api/v1/auth/sso/keycloak/start?redirect=" + site, null)
        .expectError(404, "NOT_FOUND");
    api()
        .get("/api/v1/auth/sso/keycloak/callback?code=x&state=y", null)
        .expectError(404, "NOT_FOUND");
    api()
        .get("/api/v1/auth/sso/keycloak/logout?redirect=" + site, null)
        .expectError(404, "NOT_FOUND");
    api()
        .post("/api/v1/auth/sso/exchange", null, Map.of("code", "x"))
        .expectError(404, "NOT_FOUND");
  }
}
