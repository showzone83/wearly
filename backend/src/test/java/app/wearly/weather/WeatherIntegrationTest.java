package app.wearly.weather;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.Zones;
import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/**
 * 17 문서 5·6장: 관리자 날씨 API와 앱 날씨 조회. 테스트는 목업 공급자(WEARLY_WEATHER_PROVIDER=mock)로 돌고 주기 실행은 꺼져 있다. 실제
 * WeatherAPI 호출 형식은 WeatherApiClientTest, 전환·차단 규칙은 WeatherRefreshRunnerTest가 본다.
 */
class WeatherIntegrationTest extends IntegrationTestBase {

  static final String BASE = "/api/v1/admin/weather";

  /** 지금 실행을 요청하고 끝날 때까지 기다린다 (실행 중 409면 잠깐 뒤 다시) */
  JsonNode refreshAndWait(Session admin, Map<String, Object> body) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    Resp started;
    while (true) {
      started = api().post(BASE + "/refresh", admin.accessToken(), body);
      if (started.status() != 409 || Instant.now().isAfter(deadline)) {
        break;
      }
      Thread.sleep(200);
    }
    String runId = started.expect(202).body().get("runId").asString();
    while (Instant.now().isBefore(deadline)) {
      JsonNode detail = api().get(BASE + "/runs/" + runId, admin.accessToken()).expect(200).body();
      if (!detail.get("run").get("status").asString().equals("RUNNING")) {
        return detail;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("날씨 실행이 끝나지 않았어요: " + runId);
  }

  @Test
  void 관리자만_쓸_수_있다() {
    Session user = signup();
    api().get(BASE + "/settings", user.accessToken()).expectError(403, "FORBIDDEN");
    api().post(BASE + "/refresh", user.accessToken(), Map.of()).expectError(403, "FORBIDDEN");
    api().get(BASE + "/settings", null).expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 지금_실행하면_활성_지역을_받고_앱은_저장값만_읽는다() throws InterruptedException {
    Session admin = admin();

    JsonNode detail = refreshAndWait(admin, Map.of());
    JsonNode run = detail.get("run");
    assertThat(run.get("trigger").asString()).isEqualTo("MANUAL");
    assertThat(run.get("status").asString()).isEqualTo("SUCCESS");
    assertThat(run.get("successCount").asInt()).isGreaterThanOrEqualTo(17);
    assertThat(run.get("requestedByEmail").asString()).isEqualTo(ADMIN_EMAIL);
    assertThat(detail.get("calls").size()).isEqualTo(run.get("successCount").asInt());
    assertThat(detail.get("calls").get(0).get("providerCode").asString()).isEqualTo("WEATHERAPI");

    // 앱: 로그인 없이, 지역 없으면 서울
    Resp seoul = api().get("/api/v1/weather", null).expect(200);
    JsonNode body = seoul.body();
    assertThat(body.get("region").get("code").asString()).isEqualTo("SEOUL");
    assertThat(body.get("region").get("label").asString()).isEqualTo("서울");
    assertThat(body.get("current").get("cond").asString()).isIn("SUNNY", "CLOUDY", "RAIN");
    assertThat(body.get("current").has("temp")).isTrue();
    assertThat(body.get("days").size()).isEqualTo(3);
    assertThat(body.get("days").get(0).get("date").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(body.get("stale").asBoolean()).isFalse();
    assertThat(body.has("provider")).as("앱 응답에 공급자 이름은 없다").isFalse();
    String etag = seoul.headers().getETag();
    assertThat(etag).isNotBlank();

    HttpHeaders ifNone = new HttpHeaders();
    ifNone.setIfNoneMatch(etag);
    assertThat(api().get("/api/v1/weather?region=SEOUL", null, ifNone).status()).isEqualTo(304);

    // 만료된 토큰이 같이 와도 공개 경로라 무시한다
    assertThat(api().get("/api/v1/weather?region=BUSAN", "expired.token.value").status())
        .isEqualTo(200);

    api().get("/api/v1/weather?region=NOWHERE", null).expectError(400, "VALIDATION_FAILED");

    // 저장된 날씨 목록·상세
    JsonNode list = api().get(BASE + "/snapshots", admin.accessToken()).expect(200).body();
    assertThat(list.size()).isGreaterThanOrEqualTo(17);
    JsonNode first = list.get(0);
    assertThat(first.get("regionCode").asString()).isEqualTo("SEOUL");
    assertThat(first.get("providerCode").asString()).isEqualTo("WEATHERAPI");
    assertThat(first.get("raw").isNull()).as("목록에는 원본이 없다").isTrue();
    JsonNode one = api().get(BASE + "/snapshots/SEOUL", admin.accessToken()).expect(200).body();
    assertThat(one.get("raw").get("mock").asBoolean()).isTrue();
    api().get(BASE + "/snapshots/NOWHERE", admin.accessToken()).expectError(404, "NOT_FOUND");
  }

  @Test
  void 받은_날씨는_오늘_이력에_남고_관리자가_조회한다() throws InterruptedException {
    Session admin = admin();
    refreshAndWait(admin, Map.of("regionCode", "SEOUL"));
    JsonNode page =
        api().get(BASE + "/history?regionCode=seoul", admin.accessToken()).expect(200).body();
    JsonNode row = page.get("items").get(0);
    String today = LocalDate.now(Zones.SEOUL).toString();
    assertThat(row.get("regionCode").asString()).isEqualTo("SEOUL");
    assertThat(row.get("regionLabel").asString()).isEqualTo("서울");
    assertThat(row.get("date").asString()).isEqualTo(today);
    assertThat(row.get("providerCode").asString()).isEqualTo("WEATHERAPI");
    assertThat(row.get("forecast").get("date").asString()).isEqualTo(today);
    int count = row.get("observed").get("count").asInt();
    assertThat(count).isPositive();

    // 다시 받으면 같은 행에 관측이 더해진다 (목업은 관측 시각 = 받은 시각)
    refreshAndWait(admin, Map.of("regionCode", "SEOUL"));
    JsonNode again =
        api()
            .get(
                BASE + "/history?regionCode=SEOUL&from=" + today + "&to=" + today,
                admin.accessToken())
            .expect(200)
            .body();
    assertThat(again.get("total").asLong()).isEqualTo(1);
    assertThat(again.get("items").get(0).get("observed").get("count").asInt()).isEqualTo(count + 1);

    api()
        .get(BASE + "/history?from=2026-13-01", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");
    api()
        .get(BASE + "/history?from=2026-10-10&to=2026-10-01", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");
    api().get(BASE + "/history", signup().accessToken()).expectError(403, "FORBIDDEN");
  }

  @Test
  void 한_번도_받지_못한_지역은_503이고_한_지역만_실행할_수_있다() throws InterruptedException {
    Session admin = admin();
    String code =
        "T"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
    api()
        .post(
            "/api/v1/admin/codes/REGION",
            admin.accessToken(),
            Map.of("code", code, "label", "시험", "attrs", Map.of("lat", 36.0, "lon", 127.0)))
        .expect(201);
    api()
        .post(
            "/api/v1/admin/codes/REGION",
            admin.accessToken(),
            Map.of("code", code + "X", "label", "틀림", "attrs", Map.of("lat", 91)))
        .expectError(400, "VALIDATION_FAILED");

    api().get("/api/v1/weather?region=" + code, null).expectError(503, "WEATHER_UNAVAILABLE");

    JsonNode detail = refreshAndWait(admin, Map.of("regionCode", code));
    assertThat(detail.get("run").get("regionCode").asString()).isEqualTo(code);
    assertThat(detail.get("run").get("successCount").asInt()).isEqualTo(1);
    assertThat(detail.get("calls").size()).isEqualTo(1);
    api().get("/api/v1/weather?region=" + code, null).expect(200);

    api()
        .post(BASE + "/refresh", admin.accessToken(), Map.of("regionCode", "NOWHERE"))
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 설정을_검증하고_바꾸면_변경_이력에_남는다() {
    Session admin = admin();
    JsonNode before = api().get(BASE + "/settings", admin.accessToken()).expect(200).body();
    assertThat(before.get("schedulerEnabled").asBoolean()).isFalse();
    assertThat(before.get("activeRegions").asInt()).isGreaterThanOrEqualTo(17);
    JsonNode s = before.get("setting");

    Resp bad =
        api()
            .put(
                BASE + "/settings",
                admin.accessToken(),
                Map.of(
                    "enabled", true,
                    "intervalMinutes", 5,
                    "forecastDays", 15,
                    "failureThreshold", 3,
                    "cooldownMinutes", 15))
            .expectError(400, "VALIDATION_FAILED");
    assertThat(bad.body().get("fieldErrors").toString())
        .contains("intervalMinutes")
        .contains("forecastDays");
    api()
        .put(BASE + "/settings", admin.accessToken(), Map.of("enabled", true))
        .expectError(400, "VALIDATION_FAILED");

    Map<String, Object> next = new HashMap<>();
    next.put("enabled", s.get("enabled").asBoolean());
    next.put("intervalMinutes", s.get("intervalMinutes").asInt() == 45 ? 30 : 45);
    next.put("forecastDays", s.get("forecastDays").asInt());
    next.put("failureThreshold", s.get("failureThreshold").asInt());
    next.put("cooldownMinutes", s.get("cooldownMinutes").asInt());
    JsonNode after = api().put(BASE + "/settings", admin.accessToken(), next).expect(200).body();
    assertThat(after.get("setting").get("intervalMinutes").asInt())
        .isEqualTo(next.get("intervalMinutes"));
    assertThat(after.get("setting").get("updatedAt").asString()).isNotBlank();

    JsonNode audit =
        api()
            .get("/api/v1/admin/audit-logs?targetType=WEATHER_SETTING", admin.accessToken())
            .expect(200)
            .body();
    JsonNode latest = audit.get("items").get(0);
    assertThat(latest.get("action").asString()).isEqualTo("WEATHER_SETTING_UPDATE");
    assertThat(latest.get("after").get("intervalMinutes").asInt())
        .isEqualTo(next.get("intervalMinutes"));
  }

  @Test
  void 공급자를_고치고_차단_해제하고_시험_호출한다() {
    Session admin = admin();
    JsonNode providers = api().get(BASE + "/providers", admin.accessToken()).expect(200).body();
    assertThat(providers.size()).isEqualTo(1);
    JsonNode p = providers.get(0);
    assertThat(p.get("code").asString()).isEqualTo("WEATHERAPI");
    assertThat(p.get("keyName").asString()).isEqualTo("WEATHERAPI_KEY");
    assertThat(p.get("keyState").asString()).isEqualTo("MOCK");
    assertThat(p.get("status").asString()).isEqualTo("OK");
    assertThat(p.get("callsPerFetch").asInt()).isEqualTo(1);
    assertThat(p.toString()).doesNotContain("apiKey");

    api()
        .patch(BASE + "/providers/WEATHERAPI", admin.accessToken(), Map.of("maxForecastDays", 15))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .patch(BASE + "/providers/WEATHERAPI", admin.accessToken(), Map.of("enabled", "yes"))
        .expectError(400, "VALIDATION_FAILED");
    api()
        .patch(BASE + "/providers/NOPE", admin.accessToken(), Map.of("enabled", true))
        .expectError(404, "NOT_FOUND");

    Map<String, Object> quota = new HashMap<>();
    quota.put("monthlyQuota", 1_000_000);
    JsonNode patched =
        api().patch(BASE + "/providers/WEATHERAPI", admin.accessToken(), quota).expect(200).body();
    assertThat(patched.get("monthlyQuota").asInt()).isEqualTo(1_000_000);
    quota.put("monthlyQuota", null);
    assertThat(
            api()
                .patch(BASE + "/providers/WEATHERAPI", admin.accessToken(), quota)
                .expect(200)
                .body()
                .get("monthlyQuota")
                .isNull())
        .isTrue();

    JsonNode reset =
        api()
            .post(BASE + "/providers/WEATHERAPI/reset", admin.accessToken(), Map.of())
            .expect(200)
            .body();
    assertThat(reset.get("consecutiveFailures").asInt()).isZero();

    JsonNode test =
        api()
            .post(
                BASE + "/providers/WEATHERAPI/test",
                admin.accessToken(),
                Map.of("regionCode", "BUSAN"))
            .expect(200)
            .body();
    assertThat(test.get("success").asBoolean()).isTrue();
    assertThat(test.get("regionCode").asString()).isEqualTo("BUSAN");
    assertThat(test.get("days").size()).isEqualTo(3);
    api()
        .post(BASE + "/providers/WEATHERAPI/test", admin.accessToken(), Map.of())
        .expectError(400, "VALIDATION_FAILED");

    JsonNode runs = api().get(BASE + "/runs?trigger=TEST", admin.accessToken()).expect(200).body();
    assertThat(runs.get("items").get(0).get("runId").asString())
        .isEqualTo(test.get("runId").asString());
    api()
        .get(BASE + "/runs?trigger=NOPE", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");

    JsonNode audit =
        api()
            .get("/api/v1/admin/audit-logs?targetType=WEATHER_PROVIDER", admin.accessToken())
            .expect(200)
            .body();
    assertThat(audit.get("items").get(0).get("action").asString())
        .isEqualTo("WEATHER_PROVIDER_RESET");
  }

  @Test
  void 호출_이력을_거르고_요약한다() throws InterruptedException {
    Session admin = admin();
    refreshAndWait(admin, Map.of("regionCode", "JEJU"));

    JsonNode calls =
        api()
            .get(
                BASE + "/calls?regionCode=jeju&success=true&providerCode=WEATHERAPI&size=5",
                admin.accessToken())
            .expect(200)
            .body();
    assertThat(calls.get("size").asInt()).isEqualTo(5);
    assertThat(calls.get("total").asLong()).isPositive();
    JsonNode c = calls.get("items").get(0);
    assertThat(c.get("regionCode").asString()).isEqualTo("JEJU");
    assertThat(c.get("success").asBoolean()).isTrue();
    assertThat(c.get("trigger").asString()).isEqualTo("MANUAL");
    assertThat(c.get("calledAt").asString()).endsWith("Z");

    api()
        .get(BASE + "/calls?success=maybe", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");
    api()
        .get(BASE + "/calls?from=yesterday", admin.accessToken())
        .expectError(400, "VALIDATION_FAILED");

    JsonNode summary = api().get(BASE + "/calls/summary", admin.accessToken()).expect(200).body();
    assertThat(summary.get("today").get("calls").asLong()).isPositive();
    assertThat(summary.get("months").get(0).get("providerCode").asString()).isEqualTo("WEATHERAPI");
    assertThat(summary.get("months").get(0).get("used").asLong()).isPositive();
  }
}
