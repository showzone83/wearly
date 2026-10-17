package app.wearly.auth;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 하네스처럼 짧은 Access 수명으로 띄워 실제 만료 → 갱신 흐름을 확인한다. JWT exp는 초 단위라 발급기가 올림한다: 실제 수명은 1초 이상 2초 미만이다 (전에는
 * 버림이라 0~1초여서 첫 요청이 가끔 TOKEN_EXPIRED로 실패했다).
 */
@TestPropertySource(properties = "wearly.jwt.access-ttl=PT1S")
class ShortAccessTtlIntegrationTest extends IntegrationTestBase {

  @Test
  void 수명이_지나면_TOKEN_EXPIRED이고_refresh로_새_토큰을_받는다() throws InterruptedException {
    Session s = signup();
    api().get("/api/v1/users/me", s.accessToken()).expect(200);

    Thread.sleep(2100);
    api().get("/api/v1/users/me", s.accessToken()).expectError(401, "TOKEN_EXPIRED");

    Session renewed =
        TestApi.session(
            api()
                .post("/api/v1/auth/refresh", null, Map.of("refreshToken", s.refreshToken()))
                .expect(200));
    api().get("/api/v1/users/me", renewed.accessToken()).expect(200);
  }
}
