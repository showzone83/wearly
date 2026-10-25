package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 비밀번호 변경과 변경 주기 (28 문서 1단계, 실제 HTTP·DB·Valkey): 상태 조회, 변경(다른 세션 끊김·주기 다시 시작), 연장, 로그인 이력, 관리자 연장과
 * 변경 이력
 */
class PasswordIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/users/me/password";
  static final String PASSWORD = "pass1234";

  @Autowired JdbcTemplate jdbc;

  long id(Session s) {
    return Long.parseLong(s.userId());
  }

  /** 비밀번호를 정한 시각을 days일 전으로 당긴다 */
  void age(long userId, int days) {
    jdbc.update(
        "UPDATE tb_user_local_credential SET password_changed_at = now() - make_interval(days => ?)"
            + " WHERE user_id = ?",
        days,
        userId);
  }

  List<String> events(long userId) {
    return jdbc.queryForList(
        "select type from tb_auth_event where user_id = ? order by auth_event_id",
        String.class,
        userId);
  }

  @Test
  void 가입하면_지금부터_90일이_기한이다() {
    Session me = signup();
    JsonNode s = api().get(PATH, me.accessToken()).expect(200).body();

    Instant changed = Instant.parse(s.get("changedAt").asString());
    assertThat(changed).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
    assertThat(Instant.parse(s.get("dueAt").asString()))
        .isEqualTo(changed.plus(Duration.ofDays(90)));
    assertThat(s.get("due").asBoolean()).isFalse();
    assertThat(s.get("intervalDays").asLong()).isEqualTo(90);

    api().get(PATH, null).expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 비밀번호를_바꾸면_다른_기기는_로그아웃되고_주기를_다시_센다() {
    String email = uniqueEmail();
    Session keep = api().signup(email, PASSWORD, "변경");
    final Session other =
        TestApi.session(
            api()
                .post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .expect(200));
    age(id(keep), 120);
    assertThat(api().get(PATH, keep.accessToken()).expect(200).body().get("due").asBoolean())
        .isTrue();

    JsonNode same =
        api()
            .post(
                PATH,
                keep.accessToken(),
                Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))
            .expectError(400, "VALIDATION_FAILED")
            .body();
    assertThat(same.get("fieldErrors").get(0).get("field").asString()).isEqualTo("newPassword");
    assertThat(same.get("fieldErrors").get(0).get("code").asString()).isEqualTo("NOT_ALLOWED");
    JsonNode format =
        api()
            .post(
                PATH,
                keep.accessToken(),
                Map.of("currentPassword", PASSWORD, "newPassword", "short"))
            .expectError(400, "VALIDATION_FAILED")
            .body();
    assertThat(format.get("fieldErrors").get(0).get("code").asString()).isEqualTo("INVALID_FORMAT");
    api()
        .post(
            PATH,
            keep.accessToken(),
            Map.of("currentPassword", "wrong999", "newPassword", "newpass99"))
        .expectError(403, "REAUTH_FAILED");
    api().post(PATH, keep.accessToken(), null).expectError(400, "VALIDATION_FAILED");

    api()
        .post(
            PATH,
            keep.accessToken(),
            Map.of("currentPassword", PASSWORD, "newPassword", "newpass99"))
        .expect(204);

    // 지금 기기는 그대로, 다른 기기는 끊긴다
    JsonNode s = api().get(PATH, keep.accessToken()).expect(200).body();
    assertThat(s.get("due").asBoolean()).isFalse();
    assertThat(Instant.parse(s.get("changedAt").asString()))
        .isAfter(Instant.now().minusSeconds(60));
    api().get(PATH, other.accessToken()).expectError(401, "TOKEN_INVALID");
    // 옛 비밀번호는 안 되고 새 비밀번호로 로그인한다
    api()
        .post("/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .expectError(401, "INVALID_CREDENTIALS");
    api()
        .post("/api/v1/auth/login", null, Map.of("email", email, "password", "newpass99"))
        .expect(200);
    assertThat(events(id(keep))).contains("PASSWORD_CHANGED", "FORCED_LOGOUT");
  }

  @Test
  void 연장하면_지금부터_90일_뒤가_기한이다() {
    Session me = signup();
    age(id(me), 95);
    assertThat(api().get(PATH, me.accessToken()).expect(200).body().get("due").asBoolean())
        .isTrue();

    JsonNode after = api().post(PATH + "/postpone", me.accessToken(), null).expect(200).body();

    assertThat(after.get("due").asBoolean()).isFalse();
    Instant due = Instant.parse(after.get("dueAt").asString());
    assertThat(due)
        .isBetween(
            Instant.now().plus(Duration.ofDays(90)).minusSeconds(60),
            Instant.now().plus(Duration.ofDays(90)).plusSeconds(5));
    assertThat(api().get(PATH, me.accessToken()).expect(200).body().get("dueAt").asString())
        .isEqualTo(after.get("dueAt").asString());
    assertThat(events(id(me))).endsWith("PASSWORD_POSTPONED");
  }

  @Test
  void 관리자도_상태를_보고_연장하면_변경_이력이_남는다() {
    Session admin = admin();
    JsonNode s = api().get("/api/v1/admin/me/password", admin.accessToken()).expect(200).body();
    assertThat(s.get("intervalDays").asLong()).isEqualTo(90);
    assertThat(s.has("dueAt")).isTrue();

    JsonNode after =
        api()
            .post("/api/v1/admin/me/password/postpone", admin.accessToken(), null)
            .expect(200)
            .body();
    assertThat(after.get("due").asBoolean()).isFalse();

    JsonNode items =
        api()
            .get("/api/v1/admin/audit-logs?targetType=USER&size=100", admin.accessToken())
            .body()
            .get("items");
    JsonNode audit = null;
    for (JsonNode a : items) {
      if (a.get("action").asString().equals("ADMIN_PASSWORD_POSTPONE")) {
        audit = a;
        break;
      }
    }
    assertThat(audit).isNotNull();
    assertThat(audit.get("targetId").asString()).isEqualTo(admin.userId());

    Session user = signup();
    api().get("/api/v1/admin/me/password", user.accessToken()).expectError(403, "FORBIDDEN");
  }
}
