package app.wearly.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import org.junit.jupiter.api.Test;

/** MVC 밖에서 생긴 오류도 계약 4장 형식 { status, code, message, fieldErrors }로 나간다. */
class ErrorFormatIntegrationTest extends IntegrationTestBase {

    @Test
    void 방화벽이_거절한_URL도_계약_형식() {
        // 세미콜론(경로 매개변수)은 Spring Security StrictHttpFirewall이 거절한다 → 컨테이너 /error
        Resp r = api().get("/api/v1/wardrobe/items;x=1", signup().accessToken()).expectError(400, "VALIDATION_FAILED");
        assertThat(r.body().has("timestamp")).isFalse();
        assertThat(r.body().get("fieldErrors").isArray()).isTrue();

        api().get("/api/v1/auth/login;x", null).expectError(400, "VALIDATION_FAILED");
    }
}
