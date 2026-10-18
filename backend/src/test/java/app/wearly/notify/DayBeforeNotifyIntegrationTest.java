package app.wearly.notify;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.Zones;
import app.wearly.notify.application.port.in.DayBeforeNotifyUseCase;
import app.wearly.notify.application.port.in.dto.DayBeforeRunDto;
import app.wearly.support.FakeHttpServer;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi;
import app.wearly.support.TestApi.Session;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * 서버 푸시 D-1 작업 (실제 DB·HTTP, Expo는 가짜 서버). 서버 푸시를 켜고(provider=expo) 예약 작업은 끈 채 유스케이스를 직접 부른다: 알림을 켠
 * 회원만, 하루 한 번, DeviceNotRegistered 토큰 삭제, 등록 응답의 serverPush.
 */
class DayBeforeNotifyIntegrationTest extends IntegrationTestBase {

  static final FakeHttpServer EXPO = new FakeHttpServer();
  static final String OK_TICKET = "{\"data\":[{\"status\":\"ok\",\"id\":\"receipt-1\"}]}";

  @DynamicPropertySource
  static void push(DynamicPropertyRegistry registry) {
    registry.add("wearly.notify.push.enabled", () -> "true");
    registry.add("wearly.notify.push.provider", () -> "expo");
    registry.add("wearly.notify.push.expo.base-url", EXPO::url);
    registry.add("wearly.notify.push.expo.access-token", () -> "test-expo-access");
    registry.add("wearly.notify.day-before.scheduler-enabled", () -> "false");
  }

  @AfterAll
  static void stopExpo() {
    EXPO.close();
  }

  @Autowired DayBeforeNotifyUseCase dayBefore;
  @Autowired JdbcTemplate jdbc;
  @Autowired Clock clock;

  @BeforeEach
  void reset() {
    EXPO.clear();
    EXPO.respond(200, OK_TICKET);
  }

  LocalDate today() {
    return Zones.today(clock);
  }

  static String newToken() {
    return "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]";
  }

  /** 회원 + 내일 예정 하나(clientId) + 기기 토큰 하나 */
  Session member(String clientId, String token, boolean dayBefore) {
    Session s = signup();
    if (!dayBefore) {
      putProfile(s, false);
    }
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("clientId", clientId);
    plan.put("date", today().plusDays(1).toString());
    plan.put("timeOfDay", "PM");
    plan.put("title", "결혼식 하객");
    plan.put("occasion", "WEDDING");
    plan.put(
        "weather",
        Map.of("temp", 18, "min", 13, "max", 22, "rainProb", 10, "cond", "CLOUDY", "manual", true));
    plan.put("weatherSource", "MANUAL");
    plan.put("place", Map.of("label", "도쿄", "lat", 35.68, "lon", 139.69));
    api().post("/api/v1/plans", s.accessToken(), plan).expect(201);
    JsonNode reg =
        api()
            .put(
                "/api/v1/me/push-token",
                s.accessToken(),
                Map.of("token", token, "platform", "ios", "deviceId", "dev-" + clientId))
            .expect(200)
            .body();
    // 서버 푸시가 켜져 있고 expo로 보내므로 앱은 로컬 D-1 알림을 끈다
    assertThat(reg.get("serverPush").asBoolean()).isTrue();
    return s;
  }

  void putProfile(Session s, boolean dayBefore) {
    api()
        .put(
            "/api/v1/users/me/profile",
            s.accessToken(),
            Map.of(
                "name",
                "테스터",
                "dressCodes",
                List.of("CASUAL"),
                "moods",
                List.of(),
                "colors",
                List.of("navy"),
                "workType",
                "WEEKDAY",
                "workDays",
                List.of(1),
                "workRule",
                true,
                "offRule",
                false,
                "notify",
                Map.of("morning", true, "evening", true, "dMinus1", dayBefore),
                "onboarded",
                true))
        .expect(200);
  }

  @Test
  void 알림을_켠_회원에게만_하루_한_번_보낸다() {
    String on = newToken();
    String off = newToken();
    member("plan-on-" + UUID.randomUUID().toString().substring(0, 8), on, true);
    member("plan-off-" + UUID.randomUUID().toString().substring(0, 8), off, false);

    DayBeforeRunDto first = dayBefore.runDayBefore(today());

    assertThat(first.disabled()).isFalse();
    assertThat(first.planDate()).isEqualTo(today().plusDays(1));
    assertThat(EXPO.requests()).hasSize(1);
    FakeHttpServer.Request req = EXPO.last();
    assertThat(req.path()).isEqualTo("/--/api/v2/push/send");
    assertThat(req.header("Authorization")).isEqualTo("Bearer test-expo-access");
    JsonNode messages = TestApi.JSON.readTree(req.body());
    assertThat(messages).hasSize(1);
    JsonNode m = messages.get(0);
    assertThat(m.get("to").asString()).isEqualTo(on);
    assertThat(m.get("title").asString()).isEqualTo("내일 예정 코디");
    // 직접 고른 날씨(MANUAL)는 다시 확인하지 않는다
    assertThat(m.get("body").asString()).isEqualTo("내일 ‘결혼식 하객’ 예정이에요 · 도쿄 · 직접 입력 18°C");
    assertThat(m.get("data").get("type").asString()).isEqualTo("plan-d1");
    assertThat(m.get("data").get("planId").asString()).startsWith("plan-on-");

    DayBeforeRunDto again = dayBefore.runDayBefore(today());
    assertThat(again.notified()).isZero();
    assertThat(again.alreadySent()).isGreaterThanOrEqualTo(1);
    assertThat(EXPO.requests()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_push_send_log l JOIN tb_planned_outfit p"
                    + " ON p.planned_outfit_id = l.planned_outfit_id"
                    + " WHERE p.client_id LIKE 'plan-on-%' AND l.send_date = ?",
                Long.class, today()))
        .isGreaterThanOrEqualTo(1L);
  }

  @Test
  void DeviceNotRegistered면_토큰을_지운다() {
    String token = newToken();
    Session s = member("plan-gone-" + UUID.randomUUID().toString().substring(0, 8), token, true);
    EXPO.respond(
        200,
        "{\"data\":[{\"status\":\"error\",\"message\":\"x\","
            + "\"details\":{\"error\":\"DeviceNotRegistered\"}}]}");

    DayBeforeRunDto r = dayBefore.runDayBefore(today());

    assertThat(r.removedTokens()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tb_push_token WHERE user_id = ?",
                Long.class,
                Long.parseLong(s.userId())))
        .isZero();
  }
}
