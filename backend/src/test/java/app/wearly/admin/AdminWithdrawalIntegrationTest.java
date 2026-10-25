package app.wearly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 탈퇴 기록 (doc/27 4단계, 실제 HTTP): 목록·검색·필터, 요약, 탈퇴 취소·바로 삭제와 변경 이력, 회원 목록의 탈퇴 예정 필터·삭제 예정일, 권한. 같은
 * DB를 다른 테스트와 함께 쓰므로 건수는 "이 테스트의 기록이 들어 있는지"로 확인한다
 */
class AdminWithdrawalIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/admin/withdrawals";

  @Autowired JdbcTemplate jdbc;

  Session withdrawn(String reason, String text) {
    Session s = signup();
    Map<String, Object> body =
        text == null ? Map.of("reason", reason) : Map.of("reason", reason, "reasonText", text);
    api().post("/api/v1/users/me/withdrawal", s.accessToken(), body).expect(200);
    return s;
  }

  String email(Session s) {
    return jdbc.queryForObject(
        "SELECT email FROM tb_user WHERE user_id = ?", String.class, Long.parseLong(s.userId()));
  }

  JsonNode findRow(String t, String query, String userId) {
    JsonNode items = api().get(PATH + "?size=100&q=" + query, t).expect(200).body().get("items");
    for (JsonNode r : items) {
      if (r.get("userId").asString().equals(userId)) {
        return r;
      }
    }
    throw new AssertionError("탈퇴 기록에 없음: " + userId);
  }

  JsonNode lastAudit(String t, String userId) {
    JsonNode items =
        api().get("/api/v1/admin/audit-logs?targetType=USER&size=100", t).body().get("items");
    for (JsonNode a : items) {
      if (a.get("targetId").asString().equals(userId)) {
        return a;
      }
    }
    throw new AssertionError("변경 이력에 없음: " + userId);
  }

  @Test
  void 관리자만_쓸_수_있다() {
    Session user = signup();
    for (String path : List.of(PATH, PATH + "/summary")) {
      api().get(path, user.accessToken()).expectError(403, "FORBIDDEN");
      api().get(path, null).expectError(401, "UNAUTHORIZED");
    }
    for (String action : List.of("cancel", "purge")) {
      api().post(PATH + "/1/" + action, user.accessToken(), null).expectError(403, "FORBIDDEN");
    }
  }

  @Test
  void 목록은_사유와_남은_회원을_보여_주고_검색과_필터로_거른다() {
    Session s = withdrawn("PRIVACY", "걱정돼요");
    String t = admin().accessToken();
    String mail = email(s);

    JsonNode r = findRow(t, mail, s.userId());
    assertThat(r.get("email").asString()).isEqualTo(mail);
    assertThat(r.get("reason").asString()).isEqualTo("PRIVACY");
    assertThat(r.get("reasonText").asString()).isEqualTo("걱정돼요");
    assertThat(r.get("state").asString()).isEqualTo("OPEN");
    assertThat(r.get("overdue").asBoolean()).isFalse();
    assertThat(r.get("purgeAt").asString()).endsWith("Z");
    assertThat(r.get("canceledAt").isNull()).isTrue();
    // 회원 ID로도 찾는다
    assertThat(findRow(t, s.userId(), s.userId()).get("reason").asString()).isEqualTo("PRIVACY");

    // 필터
    assertThat(
            api()
                .get(PATH + "?q=" + mail + "&state=OPEN&reason=PRIVACY", t)
                .expect(200)
                .body()
                .get("total")
                .asLong())
        .isEqualTo(1);
    assertThat(api().get(PATH + "?q=" + mail + "&state=CANCELED", t).body().get("items")).isEmpty();
    assertThat(api().get(PATH + "?q=" + mail + "&reason=OTHER", t).body().get("items")).isEmpty();
    assertThat(api().get(PATH + "?q=" + mail + "&state=OVERDUE", t).body().get("items")).isEmpty();
    api().get(PATH + "?state=GONE&reason=BORED", t).expectError(400, "VALIDATION_FAILED");

    // 회원 목록: 탈퇴 예정 필터와 삭제 예정일
    JsonNode users =
        api()
            .get("/api/v1/admin/users?status=WITHDRAWN&q=" + mail, t)
            .expect(200)
            .body()
            .get("items");
    assertThat(users).hasSize(1);
    assertThat(users.get(0).get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(users.get(0).get("purgeAt").asString()).isEqualTo(r.get("purgeAt").asString());
  }

  @Test
  void 요약은_기간_안의_상태별_사유별_수를_준다() {
    withdrawn("BAD_RECOMMEND", null);
    String t = admin().accessToken();
    JsonNode sum = api().get(PATH + "/summary", t).expect(200).body();
    assertThat(sum.get("days").asInt()).isEqualTo(30);
    assertThat(sum.get("total").asLong()).isPositive();
    assertThat(sum.get("open").asLong()).isPositive();
    assertThat(sum.get("total").asLong())
        .isEqualTo(
            sum.get("open").asLong() + sum.get("canceled").asLong() + sum.get("purged").asLong());
    assertThat(sum.get("reasons").findValuesAsString("reason")).contains("BAD_RECOMMEND");
    assertThat(api().get(PATH + "/summary?days=7", t).body().get("days").asInt()).isEqualTo(7);
    api().get(PATH + "/summary?days=0", t).expectError(400, "VALIDATION_FAILED");
    api().get(PATH + "/summary?days=366", t).expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 탈퇴_취소는_회원을_되돌리고_변경_이력을_남긴다() {
    Session s = withdrawn("OTHER", null);
    String t = admin().accessToken();
    String mail = email(s);

    api().post(PATH + "/" + s.userId() + "/cancel", t, null).expect(204);

    JsonNode r = findRow(t, mail, s.userId());
    assertThat(r.get("state").asString()).isEqualTo("CANCELED");
    assertThat(r.get("canceledBy").asString()).isEqualTo("ADMIN");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM tb_user WHERE user_id = ?",
                String.class,
                Long.parseLong(s.userId())))
        .isEqualTo("ACTIVE");
    // 회원은 그냥 로그인할 수 있다 (탈퇴 예정 안내 없이)
    api()
        .post("/api/v1/auth/login", null, Map.of("email", mail, "password", "pass1234"))
        .expect(200);

    JsonNode a = lastAudit(t, s.userId());
    assertThat(a.get("action").asString()).isEqualTo("USER_WITHDRAWAL_CANCEL");
    assertThat(a.get("before").get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(a.get("before").get("reason").asString()).isEqualTo("OTHER");
    assertThat(a.get("after").get("status").asString()).isEqualTo("ACTIVE");

    // 탈퇴 예정이 아니면 404
    api().post(PATH + "/" + s.userId() + "/cancel", t, null).expectError(404, "NOT_FOUND");
    api().post(PATH + "/" + s.userId() + "/purge", t, null).expectError(404, "NOT_FOUND");
    api().post(PATH + "/999999999/cancel", t, null).expectError(404, "NOT_FOUND");
  }

  @Test
  void 바로_삭제는_회원을_지우고_기록과_변경_이력은_남는다() {
    Session s = withdrawn("NOT_USEFUL", null);
    String t = admin().accessToken();
    long id = Long.parseLong(s.userId());

    api().post(PATH + "/" + s.userId() + "/purge", t, null).expect(204);

    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM tb_user WHERE user_id = ?", Long.class, id))
        .isZero();
    JsonNode r = findRow(t, s.userId(), s.userId());
    assertThat(r.get("state").asString()).isEqualTo("PURGED");
    assertThat(r.get("purgedBy").asString()).isEqualTo("ADMIN");
    assertThat(r.get("email").isNull()).isTrue();
    assertThat(r.get("name").isNull()).isTrue();

    JsonNode a = lastAudit(t, s.userId());
    assertThat(a.get("action").asString()).isEqualTo("USER_PURGE");
    assertThat(a.get("after").get("purged").asBoolean()).isTrue();
    api().post(PATH + "/" + s.userId() + "/purge", t, null).expectError(404, "NOT_FOUND");
  }
}
