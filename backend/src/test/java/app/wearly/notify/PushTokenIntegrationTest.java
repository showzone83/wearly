package app.wearly.notify;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** 푸시 토큰 등록·삭제 (실제 HTTP, 기본 설정 = 서버 푸시 꺼짐): 회원·기기당 하나, 같은 토큰은 한 곳만, 검증, 탈퇴 시 삭제 */
class PushTokenIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/me/push-token";

  @Autowired JdbcTemplate jdbc;

  static String newToken() {
    return "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]";
  }

  static Map<String, Object> body(String token, String platform, String deviceId) {
    return Map.of("token", token, "platform", platform, "deviceId", deviceId);
  }

  List<Map<String, Object>> rows(String userId) {
    return jdbc.queryForList(
        "SELECT device_id, platform, token FROM tb_push_token WHERE user_id = ? ORDER BY device_id",
        Long.parseLong(userId));
  }

  @Test
  void 등록하면_기기마다_하나이고_같은_기기는_바꾼다() {
    Session s = signup();
    String t1 = newToken();

    JsonNode res =
        api().put(PATH, s.accessToken(), body(t1, "ios", "dev-phone")).expect(200).body();
    assertThat(res.get("deviceId").asString()).isEqualTo("dev-phone");
    assertThat(res.get("platform").asString()).isEqualTo("ios");
    // 기본 설정(wearly.notify.push.enabled=false)에서는 서버가 보내지 않으므로 앱은 로컬 알림을 쓴다
    assertThat(res.get("serverPush").asBoolean()).isFalse();
    assertThat(res.get("dayBeforeTime").asString()).isEqualTo("19:30");
    assertThat(res.properties()).hasSize(4);

    String t2 = newToken();
    api().put(PATH, s.accessToken(), body(t2, "IOS", "dev-phone")).expect(200);
    api().put(PATH, s.accessToken(), body(newToken(), "android", "dev-tab")).expect(200);
    List<Map<String, Object>> rows = rows(s.userId());
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("device_id", "dev-phone").containsEntry("token", t2);
    assertThat(rows.get(0)).containsEntry("platform", "IOS");
    assertThat(rows.get(1)).containsEntry("platform", "ANDROID");
  }

  @Test
  void 같은_토큰을_다른_회원이_등록하면_옮긴다() {
    Session a = signup();
    Session b = signup();
    String token = newToken();
    api().put(PATH, a.accessToken(), body(token, "ios", "shared-device")).expect(200);
    api().put(PATH, b.accessToken(), body(token, "ios", "shared-device")).expect(200);
    assertThat(rows(a.userId())).isEmpty();
    assertThat(rows(b.userId())).hasSize(1);
  }

  @Test
  void 지우기는_그_기기만_없어도_204() {
    Session s = signup();
    api().put(PATH, s.accessToken(), body(newToken(), "ios", "dev-a")).expect(200);
    api().put(PATH, s.accessToken(), body(newToken(), "ios", "dev-b")).expect(200);

    api().delete(PATH + "?deviceId=dev-a", s.accessToken()).expect(204);
    assertThat(rows(s.userId())).extracting(r -> r.get("device_id")).containsExactly("dev-b");
    api().delete(PATH + "?deviceId=dev-a", s.accessToken()).expect(204);

    // 남의 기기 ID로 지워도 내 것만 지운다 (다른 회원 토큰은 그대로)
    Session other = signup();
    api().delete(PATH + "?deviceId=dev-b", other.accessToken()).expect(204);
    assertThat(rows(s.userId())).hasSize(1);
  }

  @Test
  void 검증_오류는_필드별로_모아_준다() {
    Session s = signup();
    Resp r =
        api()
            .put(PATH, s.accessToken(), body("not-an-expo-token", "web", "기기 1"))
            .expectError(400, "VALIDATION_FAILED");
    JsonNode errors = r.body().get("fieldErrors");
    assertThat(errors).hasSize(3);
    assertThat(errors.get(0).get("field").asString()).isEqualTo("token");
    assertThat(errors.get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
    assertThat(errors.get(1).get("field").asString()).isEqualTo("platform");
    assertThat(errors.get(1).get("code").asString()).isEqualTo("NOT_ALLOWED");
    assertThat(errors.get(1).get("params").get("allowed").toString())
        .isEqualTo("[\"ios\",\"android\"]");
    assertThat(errors.get(2).get("field").asString()).isEqualTo("deviceId");
    assertThat(errors.get(2).get("code").asString()).isEqualTo("INVALID_FORMAT");

    Resp empty = api().put(PATH, s.accessToken(), Map.of()).expectError(400, "VALIDATION_FAILED");
    assertThat(empty.body().get("fieldErrors")).hasSize(3);

    Resp noDevice = api().delete(PATH, s.accessToken()).expectError(400, "VALIDATION_FAILED");
    assertThat(noDevice.body().get("fieldErrors").get(0).get("field").asString())
        .isEqualTo("deviceId");
    assertThat(noDevice.body().get("fieldErrors").get(0).get("code").asString())
        .isEqualTo("REQUIRED");
    assertThat(rows(s.userId())).isEmpty();
  }

  @Test
  void 로그인이_필요하다() {
    api().put(PATH, null, body(newToken(), "ios", "dev")).expectError(401, "UNAUTHORIZED");
    api().delete(PATH + "?deviceId=dev", null).expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 탈퇴하면_토큰도_지운다() {
    Session s = signup();
    api().put(PATH, s.accessToken(), body(newToken(), "android", "dev")).expect(200);
    assertThat(rows(s.userId())).hasSize(1);
    api().delete("/api/v1/users/me", s.accessToken()).expect(204);
    assertThat(rows(s.userId())).isEmpty();
  }
}
