package app.wearly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.auth.application.port.in.PasswordReminderUseCase;
import app.wearly.auth.application.port.in.dto.PasswordReminderRunDto;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * 비밀번호 변경 알림 (28 문서 2단계, 실제 DB·HTTP, Expo는 가짜 서버): 기한이 지난 활성 회원에게 주기마다 한 번 푸시. 서버 푸시를 켜고 예약 작업은 끈 채
 * 유스케이스를 직접 부른다
 */
class PasswordReminderIntegrationTest extends IntegrationTestBase {

  static final FakeHttpServer EXPO = new FakeHttpServer();
  static final String OK_TICKET = "{\"data\":[{\"status\":\"ok\",\"id\":\"receipt-1\"}]}";

  @DynamicPropertySource
  static void push(DynamicPropertyRegistry registry) {
    registry.add("wearly.notify.push.enabled", () -> "true");
    registry.add("wearly.notify.push.provider", () -> "expo");
    registry.add("wearly.notify.push.expo.base-url", EXPO::url);
    registry.add("wearly.notify.day-before.scheduler-enabled", () -> "false");
    registry.add("wearly.pairing.notify.scheduler-enabled", () -> "false");
  }

  @AfterAll
  static void stopExpo() {
    EXPO.close();
  }

  @Autowired PasswordReminderUseCase reminders;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() {
    EXPO.clear();
    EXPO.respond(200, OK_TICKET);
    // DB는 다른 테스트와 함께 쓴다: 앞선 테스트의 회원은 이미 알린 것으로 둔다 (이 테스트의 대상이 아니다)
    jdbc.update("UPDATE tb_user_local_credential SET password_reminded_at = now()");
  }

  /** 회원 + 기기 토큰 하나. 토큰 */
  String withToken(Session s) {
    String token = "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]";
    api()
        .put(
            "/api/v1/me/push-token",
            s.accessToken(),
            Map.of("token", token, "platform", "ios", "deviceId", "dev-" + s.userId()))
        .expect(200);
    return token;
  }

  /** 비밀번호를 정한 시각(과 연장 시각)을 days일 전으로 당긴다 */
  void age(Session s, int days) {
    jdbc.update(
        "UPDATE tb_user_local_credential"
            + " SET password_changed_at = now() - make_interval(days => ?),"
            + " password_postponed_at = NULL WHERE user_id = ?",
        days,
        Long.parseLong(s.userId()));
  }

  /** Expo가 받은 푸시 중 이 토큰으로 간 것 */
  List<JsonNode> pushesTo(String token) throws Exception {
    List<JsonNode> out = new ArrayList<>();
    for (FakeHttpServer.Request r : EXPO.requests()) {
      for (JsonNode m : TestApi.JSON.readTree(r.body())) {
        if (m.get("to").asString().equals(token)) {
          out.add(m);
        }
      }
    }
    return out;
  }

  Instant remindedAt(Session s) {
    return jdbc.queryForObject(
        "SELECT password_reminded_at FROM tb_user_local_credential WHERE user_id = ?",
        Instant.class,
        Long.parseLong(s.userId()));
  }

  @Test
  void 기한이_지난_회원에게만_주기마다_한_번_보낸다() throws Exception {
    Session fresh = signup();
    final String freshToken = withToken(fresh);
    Session old = signup();
    final String oldToken = withToken(old);
    age(old, 91);
    // 이 테스트의 두 회원만 대상이 되게 reset 뒤에 만든 회원(알림 기록 없음)만 남긴다
    jdbc.update(
        "UPDATE tb_user_local_credential SET password_reminded_at = NULL WHERE user_id IN (?, ?)",
        Long.parseLong(fresh.userId()),
        Long.parseLong(old.userId()));

    PasswordReminderRunDto first = reminders.remindDue(Instant.now());

    assertThat(first).isEqualTo(new PasswordReminderRunDto(1, 1, 1, 0, 0));
    assertThat(pushesTo(freshToken)).isEmpty();
    List<JsonNode> sent = pushesTo(oldToken);
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0).get("title").asString()).isEqualTo("비밀번호를 바꿔 주세요");
    assertThat(sent.get(0).get("body").asString()).contains("90일");
    assertThat(sent.get(0).get("data").get("type").asString()).isEqualTo("password-change-due");
    assertThat(sent.get(0).get("channelId").asString()).isEqualTo("account");
    assertThat(remindedAt(old)).isNotNull();

    // 같은 주기에는 다시 보내지 않는다
    assertThat(reminders.remindDue(Instant.now()).due()).isZero();
    assertThat(pushesTo(oldToken)).hasSize(1);
  }

  @Test
  void 연장하면_다음_기한이_지났을_때_한_번_더_보낸다() throws Exception {
    Session me = signup();
    final String token = withToken(me);
    age(me, 100);
    jdbc.update(
        "UPDATE tb_user_local_credential SET password_reminded_at = now() - interval '9 days'"
            + " WHERE user_id = ?",
        Long.parseLong(me.userId()));
    // 9일 전에 이미 알렸다 → 이번 주기에는 없음
    assertThat(reminders.remindDue(Instant.now()).due()).isZero();

    api().post("/api/v1/users/me/password/postpone", me.accessToken(), null).expect(200);
    // 연장 직후에는 기한 전
    assertThat(reminders.remindDue(Instant.now()).due()).isZero();
    // 91일 뒤 (연장한 시각 + 90일이 지남)
    PasswordReminderRunDto later = reminders.remindDue(Instant.now().plus(Duration.ofDays(91)));
    assertThat(later.reminded()).isGreaterThanOrEqualTo(1);
    assertThat(pushesTo(token)).hasSize(1);
  }

  @Test
  void 정지_회원과_비밀번호를_바꾼_회원에게는_보내지_않는다() throws Exception {
    Session suspended = signup();
    final String suspendedToken = withToken(suspended);
    age(suspended, 120);
    jdbc.update(
        "UPDATE tb_user SET status = 'SUSPENDED' WHERE user_id = ?",
        Long.parseLong(suspended.userId()));
    Session changed = signup();
    final String changedToken = withToken(changed);
    age(changed, 120);
    api()
        .post(
            "/api/v1/users/me/password",
            changed.accessToken(),
            Map.of("currentPassword", "pass1234", "newPassword", "newpass99"))
        .expect(204);
    jdbc.update(
        "UPDATE tb_user_local_credential SET password_reminded_at = NULL WHERE user_id IN (?, ?)",
        Long.parseLong(suspended.userId()),
        Long.parseLong(changed.userId()));

    assertThat(reminders.remindDue(Instant.now()).due()).isZero();
    assertThat(pushesTo(suspendedToken)).isEmpty();
    assertThat(pushesTo(changedToken)).isEmpty();
    assertThat(remindedAt(suspended)).isNull();
  }
}
