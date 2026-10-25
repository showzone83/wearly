package app.wearly.user;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 탈퇴 유예 (doc/27 1단계, 실제 HTTP·DB·Valkey): 신청하면 탈퇴 예정이 되고 세션·푸시 토큰·옷장 도우미 연결이 정리된다. 다시 로그인하면 403
 * ACCOUNT_WITHDRAWN, cancelWithdrawal이면 되돌려 옷장 그대로 쓴다. 탈퇴 기록이 남는다
 */
class WithdrawalIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/users/me/withdrawal";
  static final String PASSWORD = "pass1234";

  @Autowired JdbcTemplate jdbc;

  record Member(String email, Session session) {
    long id() {
      return Long.parseLong(session.userId());
    }

    String token() {
      return session.accessToken();
    }
  }

  Member member(String name) {
    String email = uniqueEmail();
    return new Member(email, api().signup(email, PASSWORD, name));
  }

  Resp login(String email, Boolean cancelWithdrawal) {
    Map<String, Object> body = new HashMap<>();
    body.put("email", email);
    body.put("password", PASSWORD);
    if (cancelWithdrawal != null) {
      body.put("cancelWithdrawal", cancelWithdrawal);
    }
    return api().post("/api/v1/auth/login", null, body);
  }

  static Map<String, Object> item(String name) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("category", "TOP");
    m.put("subType", "SHIRT");
    m.put("color", "white");
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", "OWNED");
    return m;
  }

  String status(long userId) {
    return jdbc.queryForObject(
        "SELECT status FROM tb_user WHERE user_id = ?", String.class, userId);
  }

  @Test
  void 사유는_꼭_고르고_하고_싶은_말은_200자까지() {
    Member m = member("민지");
    JsonNode none =
        api().post(PATH, m.token(), Map.of()).expectError(400, "VALIDATION_FAILED").body();
    assertThat(none.get("fieldErrors").get(0).get("field").asString()).isEqualTo("reason");
    assertThat(none.get("fieldErrors").get(0).get("code").asString()).isEqualTo("REQUIRED");
    api().post(PATH, m.token(), Map.of("reason", "other")).expectError(400, "VALIDATION_FAILED");
    JsonNode tooLong =
        api()
            .post(PATH, m.token(), Map.of("reason", "OTHER", "reasonText", "가".repeat(201)))
            .expectError(400, "VALIDATION_FAILED")
            .body();
    assertThat(tooLong.get("fieldErrors").get(0).get("field").asString()).isEqualTo("reasonText");
    // 틀린 입력으로는 아무것도 바뀌지 않는다
    assertThat(status(m.id())).isEqualTo("ACTIVE");
    api().get("/api/v1/users/me", m.token()).expect(200);
    api().post(PATH, null, Map.of("reason", "OTHER")).expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 신청하면_탈퇴_예정이_되고_세션과_푸시_토큰이_정리되고_기록이_남는다() {
    Member m = member("민지");
    api().post("/api/v1/wardrobe/items", m.token(), item("셔츠")).expect(201);
    api()
        .put(
            "/api/v1/me/push-token",
            m.token(),
            Map.of(
                "token",
                "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]",
                "platform",
                "ios",
                "deviceId",
                "dev-" + m.id()))
        .expect(200);

    Instant before = Instant.now();
    JsonNode r =
        api()
            .post(PATH, m.token(), Map.of("reason", "HARD_TO_USE", "reasonText", "  어려워요  "))
            .expect(200)
            .body();
    Instant purgeAt = Instant.parse(r.get("purgeAt").asString());
    assertThat(Duration.between(before, purgeAt))
        .isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30).plusMinutes(1));
    assertThat(Instant.parse(r.get("requestedAt").asString())).isBefore(purgeAt);

    assertThat(status(m.id())).isEqualTo("WITHDRAWN");
    api().get("/api/v1/users/me", m.token()).expectError(401, "TOKEN_INVALID");
    api()
        .post("/api/v1/auth/refresh", null, Map.of("refreshToken", m.session().refreshToken()))
        .expectError(401, "REFRESH_INVALID");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_push_token WHERE user_id = ?", Long.class, m.id()))
        .isZero();
    // 옷은 취소할 수 있도록 남는다
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_wardrobe_item WHERE user_id = ?", Long.class, m.id()))
        .isEqualTo(1);
    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM tb_user_withdrawal WHERE user_id = ?", m.id());
    assertThat(row.get("reason")).isEqualTo("HARD_TO_USE");
    assertThat(row.get("reason_text")).isEqualTo("어려워요");
    assertThat(row.get("canceled_at")).isNull();
    assertThat(row.get("purged_at")).isNull();
  }

  @Test
  void 다시_로그인하면_403이고_취소를_고르면_되돌려_옷장을_그대로_쓴다() {
    Member m = member("민지");
    api().post("/api/v1/wardrobe/items", m.token(), item("셔츠")).expect(201);
    String purgeAt =
        api()
            .post(PATH, m.token(), Map.of("reason", "OTHER_APP"))
            .expect(200)
            .body()
            .get("purgeAt")
            .asString();

    // 비밀번호가 틀리면 탈퇴 예정인지 알리지 않는다
    api()
        .post("/api/v1/auth/login", null, Map.of("email", m.email(), "password", "wrong123"))
        .expectError(401, "INVALID_CREDENTIALS");
    JsonNode blocked = login(m.email(), null).expectError(403, "ACCOUNT_WITHDRAWN").body();
    assertThat(blocked.get("params").get("purgeAt").asString()).isEqualTo(purgeAt);
    login(m.email(), false).expectError(403, "ACCOUNT_WITHDRAWN");

    Session back = TestApi.session(login(m.email(), true).expect(200));
    assertThat(back.userId()).isEqualTo(m.session().userId());
    assertThat(status(m.id())).isEqualTo("ACTIVE");
    assertThat(
            api().get("/api/v1/wardrobe/items", back.accessToken()).expect(200).body().get("items"))
        .hasSize(1);
    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM tb_user_withdrawal WHERE user_id = ?", m.id());
    assertThat(row.get("canceled_at")).isNotNull();
    assertThat(row.get("canceled_by")).isEqualTo("USER");

    // 취소한 뒤 다시 탈퇴할 수 있다 (열린 기록은 하나뿐, 닫힌 기록은 남는다)
    api().post(PATH, back.accessToken(), Map.of("reason", "OTHER")).expect(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_user_withdrawal WHERE user_id = ?", Long.class, m.id()))
        .isEqualTo(2);
  }

  @Test
  void 신청하면_옷장_도우미_연결이_끝나고_코드는_쓸_수_없다() {
    Member owner = member("엄마");
    Member helper = member("딸");
    String code =
        api()
            .post("/api/v1/pairings/codes", owner.token(), Map.of())
            .expect(201)
            .body()
            .get("code")
            .asString();
    String id =
        api()
            .post("/api/v1/pairings/join", helper.token(), Map.of("code", code))
            .expect(201)
            .body()
            .get("id")
            .asString();
    api()
        .post("/api/v1/pairings/" + id + "/approve", owner.token(), Map.of("permission", "EDIT"))
        .expect(200);
    String unused =
        api()
            .post("/api/v1/pairings/codes", owner.token(), Map.of())
            .expect(201)
            .body()
            .get("code")
            .asString();

    api().post(PATH, owner.token(), Map.of("reason", "OTHER")).expect(200);

    assertThat(
            jdbc.queryForObject(
                "SELECT ended_by FROM tb_closet_pairing WHERE pairing_id = ?",
                String.class,
                Long.parseLong(id)))
        .isEqualTo("WITHDRAWN");
    assertThat(api().get("/api/v1/pairings", helper.token()).body().get("asHelper")).isEmpty();
    api()
        .get("/api/v1/owners/" + owner.id() + "/wardrobe/items", helper.token())
        .expectError(404, "NOT_FOUND");
    // 탈퇴 예정인 회원이 만들어 둔 코드는 없는 코드다
    api()
        .post("/api/v1/pairings/join", member("남").token(), Map.of("code", unused))
        .expectError(404, "PAIRING_CODE_INVALID");
  }

  @Test
  void 탈퇴_예정인_회원의_초대_코드로는_추천인이_이어지지_않는다() {
    Member a = member("초대함");
    final String code =
        api().get("/api/v1/users/me/invite", a.token()).body().get("inviteCode").asString();
    api().post(PATH, a.token(), Map.of("reason", "OTHER")).expect(200);

    Map<String, Object> body = new HashMap<>();
    body.put("email", uniqueEmail());
    body.put("password", PASSWORD);
    body.put("name", "초대받음");
    body.put("inviteCode", code);
    Session invited = TestApi.session(api().post("/api/v1/auth/signup", null, body).expect(201));
    assertThat(
            jdbc.queryForObject(
                "SELECT result FROM tb_invite_signup WHERE user_id = ?",
                String.class,
                Long.parseLong(invited.userId())))
        .isEqualTo("UNKNOWN_CODE");
  }

  @Test
  void 관리자는_상태_바꾸기로_탈퇴_예정을_넣거나_뺄_수_없다() {
    Member m = member("민지");
    String t = admin().accessToken();
    api()
        .patch("/api/v1/admin/users/" + m.id(), t, Map.of("status", "WITHDRAWN"))
        .expectError(400, "VALIDATION_FAILED");
    api().post(PATH, m.token(), Map.of("reason", "OTHER")).expect(200);
    api()
        .patch("/api/v1/admin/users/" + m.id(), t, Map.of("status", "ACTIVE"))
        .expectError(400, "VALIDATION_FAILED");
    assertThat(status(m.id())).isEqualTo("WITHDRAWN");
  }
}
