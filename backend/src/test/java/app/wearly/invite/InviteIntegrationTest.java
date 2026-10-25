package app.wearly.invite;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.invite.application.port.out.InviteSharePolicyPort;
import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSharePolicy.Gate;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** 친구 초대 (doc/25, 실제 HTTP): 코드 발급, 초대한 이력, 초대 코드 가입 이력, 관리자 목록 */
class InviteIntegrationTest extends IntegrationTestBase {

  static final String PATH = "/api/v1/users/me/invite";

  @Autowired InviteSharePolicyPort sharePolicy;

  Session signupWith(String inviteCode) {
    Map<String, Object> body = new HashMap<>();
    body.put("email", uniqueEmail());
    body.put("password", "pass1234");
    body.put("name", "초대받음");
    body.put("inviteCode", inviteCode);
    return TestApi.session(api().post("/api/v1/auth/signup", null, body).expect(201));
  }

  JsonNode findUser(String adminToken, String userId) {
    JsonNode items = api().get("/api/v1/admin/users?size=100", adminToken).body().get("items");
    for (JsonNode u : items) {
      if (u.get("id").asString().equals(userId)) {
        return u;
      }
    }
    throw new AssertionError("회원 목록에 없음: " + userId);
  }

  @Test
  void 코드는_처음_열_때_만들고_다시_열어도_같다() {
    Session a = signup();
    JsonNode first = api().get(PATH, a.accessToken()).expect(200).body();
    String code = first.get("inviteCode").asString();
    assertThat(code).matches("[A-HJKMNP-Z2-9]{8}");
    assertThat(first.get("invitedCount").asLong()).isZero();
    assertThat(first.properties()).hasSize(2);
    assertThat(api().get(PATH, a.accessToken()).body().get("inviteCode").asString())
        .isEqualTo(code);
  }

  @Test
  void 코드로_가입하면_친구_목록과_관리자_목록에_추천인이_보인다() {
    Session a = signup();
    String code = api().get(PATH, a.accessToken()).body().get("inviteCode").asString();

    // 소문자·하이픈으로 넣어도 같은 코드
    String typed = (code.substring(0, 4) + "-" + code.substring(4)).toLowerCase();
    Session b = signupWith(typed);

    JsonNode me = api().get(PATH, a.accessToken()).body();
    assertThat(me.get("invitedCount").asLong()).isEqualTo(1);
    JsonNode friends = api().get(PATH + "/friends", a.accessToken()).expect(200).body();
    assertThat(friends).hasSize(1);
    assertThat(friends.get(0).get("name").asString()).isEqualTo("초대받음");
    assertThat(friends.get(0).get("joinedAt").asString()).endsWith("Z");

    String t = admin().accessToken();
    JsonNode userRow = findUser(t, b.userId());
    assertThat(userRow.get("referrerId").asString()).isEqualTo(a.userId());
    assertThat(userRow.get("referrerName").asString()).isNotBlank();
    assertThat(findUser(t, a.userId()).get("invitedCount").asLong()).isEqualTo(1);
    assertThat(findUser(t, a.userId()).get("referrerId").isNull()).isTrue();

    JsonNode history =
        api().get("/api/v1/admin/invites/signups?q=" + code + "&size=100", t).expect(200).body();
    assertThat(history.get("total").asLong()).isEqualTo(1);
    JsonNode row = history.get("items").get(0);
    assertThat(row.get("userId").asString()).isEqualTo(b.userId());
    assertThat(row.get("enteredCode").asString()).isEqualTo(code);
    assertThat(row.get("result").asString()).isEqualTo("LINKED");
    assertThat(row.get("referrerId").asString()).isEqualTo(a.userId());
  }

  @Test
  void 없는_코드로도_가입되고_이력에_UNKNOWN_CODE로_남는다() {
    Session c = signupWith("ZZZZ-ZZZ9");
    String t = admin().accessToken();
    assertThat(findUser(t, c.userId()).get("referrerId").isNull()).isTrue();

    JsonNode history =
        api()
            .get("/api/v1/admin/invites/signups?q=ZZZZZZZ9&result=UNKNOWN_CODE&size=100", t)
            .expect(200)
            .body();
    assertThat(history.get("items").findValuesAsString("userId")).contains(c.userId());
    JsonNode row = history.get("items").get(0);
    assertThat(row.get("referrerId").isNull()).isTrue();
  }

  @Test
  void 코드_없이_가입하면_이력이_없다() {
    Session d = signupWith(null);
    String t = admin().accessToken();
    JsonNode history = api().get("/api/v1/admin/invites/signups?size=100", t).body();
    assertThat(history.get("items").findValuesAsString("userId")).doesNotContain(d.userId());
  }

  @Test
  void 초대한_이력을_남기고_관리자가_본다() {
    Session a = signup();
    api().post(PATH + "/shares", a.accessToken(), Map.of("channel", "share")).expect(204);
    api().post(PATH + "/shares", a.accessToken(), Map.of("channel", "COPY")).expect(204);
    api()
        .post(PATH + "/shares", a.accessToken(), Map.of("channel", "KAKAO"))
        .expectError(400, "VALIDATION_FAILED");

    String t = admin().accessToken();
    String email = api().get("/api/v1/users/me", a.accessToken()).body().get("email").asString();
    JsonNode shares = api().get("/api/v1/admin/invites/shares?q=" + email, t).expect(200).body();
    assertThat(shares.get("total").asLong()).isEqualTo(2);
    assertThat(shares.get("items").findValuesAsString("channel")).containsExactly("COPY", "SHARE");
    JsonNode sms = api().get("/api/v1/admin/invites/shares?q=" + email + "&channel=SMS", t).body();
    assertThat(sms.get("total").asLong()).isZero();
    api().get("/api/v1/admin/invites/shares?channel=FAX", t).expectError(400, "VALIDATION_FAILED");
    api()
        .get("/api/v1/admin/invites/signups?result=MAYBE", t)
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 같은_방법을_연속으로_보내면_한_번만_남는다() {
    Session a = signup();
    for (int i = 0; i < 5; i++) {
      api().post(PATH + "/shares", a.accessToken(), Map.of("channel", "COPY")).expect(204);
    }
    api().post(PATH + "/shares", a.accessToken(), Map.of("channel", "SMS")).expect(204);

    String email = api().get("/api/v1/users/me", a.accessToken()).body().get("email").asString();
    JsonNode shares =
        api().get("/api/v1/admin/invites/shares?q=" + email, admin().accessToken()).body();
    assertThat(shares.get("items").findValuesAsString("channel")).containsExactly("SMS", "COPY");
  }

  @Test
  void 하루_한도를_넘으면_쿨다운_없이도_기록하지_않는다() {
    // 실제 한도(30)·쿨다운(60초)으로는 테스트가 길어져 작은 값으로 Valkey 스크립트를 직접 확인한다
    long userId = 9_000_000_000L + System.nanoTime() % 1_000_000_000L;
    LocalDate day = LocalDate.of(2026, 10, 25);
    Duration cool = Duration.ofSeconds(60);

    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.SHARE, day, cool, 2))
        .isEqualTo(Gate.RECORD);
    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.SHARE, day, cool, 2))
        .isEqualTo(Gate.COOLDOWN);
    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.SMS, day, cool, 2))
        .isEqualTo(Gate.RECORD);
    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.COPY, day, cool, 2))
        .isEqualTo(Gate.DAILY_LIMIT_FIRST);
    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.COPY, day, cool, 2))
        .isEqualTo(Gate.DAILY_LIMIT);
    // 날짜가 바뀌면 다시 센다 (쿨다운은 날짜와 상관없이 남는다)
    assertThat(sharePolicy.tryAcquire(userId, InviteChannel.COPY, day.plusDays(1), cool, 2))
        .isEqualTo(Gate.RECORD);
  }

  @Test
  void 초대_API는_로그인이_필요하고_관리자_이력은_관리자만() {
    api().get(PATH, null).expectError(401, "UNAUTHORIZED");
    Resp r = api().get("/api/v1/admin/invites/signups", signup().accessToken());
    r.expectError(403, "FORBIDDEN");
  }
}
