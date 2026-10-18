package app.wearly.weather;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.IntegrationTestBase;
import app.wearly.support.TestApi.Resp;
import app.wearly.support.TestApi.Session;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 19 문서: 장소별 날씨 API (실제 HTTP). 테스트는 목업 공급자·목업 평년값(WEARLY_WEATHER_PROVIDER=mock)으로 돌고 좌표 요청 한도는 3이다.
 * region 방식은 로그인 없이(앱은 토큰 없는 region 요청이 401이면 예전 서버로 본다), 좌표 방식과 검색은 로그인 필요.
 */
class PlaceWeatherIntegrationTest extends IntegrationTestBase {

  static final Set<String> KEYS =
      Set.of("place", "date", "source", "day", "forecastUntil", "fetchedAt", "climate");

  static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  /** 서울을 한 번 받아 둔다 (관리자 지금 실행, 실행 중이면 잠깐 뒤 다시) */
  void refreshSeoul() throws InterruptedException {
    Session admin = admin();
    Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
    Resp started;
    while (true) {
      started =
          api()
              .post(
                  "/api/v1/admin/weather/refresh",
                  admin.accessToken(),
                  Map.of("regionCode", "SEOUL"));
      if (started.status() != 409 || Instant.now().isAfter(deadline)) {
        break;
      }
      Thread.sleep(200);
    }
    String runId = started.expect(202).body().get("runId").asString();
    while (Instant.now().isBefore(deadline)) {
      JsonNode run =
          api()
              .get("/api/v1/admin/weather/runs/" + runId, admin.accessToken())
              .expect(200)
              .body()
              .get("run");
      if (!run.get("status").asString().equals("RUNNING")) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("날씨 실행이 끝나지 않았어요");
  }

  /** 다른 테스트·실행과 겹치지 않는 남반구 바다 위 격자 */
  static double[] randomPlace() {
    ThreadLocalRandom r = ThreadLocalRandom.current();
    return new double[] {-60 + r.nextInt(0, 200) / 10.0, -170 + r.nextInt(0, 3000) / 10.0};
  }

  static String grid(double lat, double lon) {
    return String.format(
        Locale.ROOT, "%.1f,%.1f", Math.round(lat * 10) / 10.0, Math.round(lon * 10) / 10.0);
  }

  Resp coords(String token, double lat, double lon, LocalDate date) {
    return api()
        .get(
            "/api/v1/weather/day?lat={lat}&lon={lon}&date={date}",
            token,
            Map.of("lat", lat, "lon", lon, "date", date.toString()));
  }

  /** 관리자 호출 이력에서 그 격자의 호출 */
  JsonNode callsFor(String gridKey) {
    return api()
        .get(
            "/api/v1/admin/weather/calls?regionCode={g}&size=50",
            admin().accessToken(),
            Map.of("g", gridKey))
        .expect(200)
        .body()
        .get("items");
  }

  @Test
  void region_방식은_토큰_없이_FORECAST와_CLIMATE를_준다() throws InterruptedException {
    refreshSeoul();

    Resp res = api().get("/api/v1/weather/day?region=SEOUL&date=" + today(), null).expect(200);
    JsonNode b = res.body();
    Set<String> keys = new HashSet<>();
    b.propertyNames().forEach(keys::add);
    assertThat(keys).isEqualTo(KEYS);
    assertThat(b.get("source").asString()).isEqualTo("FORECAST");
    assertThat(b.get("date").asString()).isEqualTo(today().toString());
    JsonNode place = b.get("place");
    assertThat(place.get("regionCode").asString()).isEqualTo("SEOUL");
    assertThat(place.get("label").asString()).isEqualTo("서울");
    assertThat(place.get("lat").asDouble()).isEqualTo(37.5665);
    assertThat(place.get("lon").asDouble()).isEqualTo(126.978);
    assertThat(b.get("day").get("cond").asString()).isIn("SUNNY", "CLOUDY", "RAIN");
    assertThat(b.get("day").has("temp")).isTrue();
    assertThat(b.get("day").get("rainProb").isInt()).isTrue();
    // 예보 일수는 관리자 설정(기본 3일)을 따른다
    assertThat(LocalDate.parse(b.get("forecastUntil").asString())).isAfterOrEqualTo(today());
    assertThat(b.get("fetchedAt").asString()).endsWith("Z");
    assertThat(b.get("climate").isNull()).isTrue();

    // 예보 밖 날짜: 서울 격자의 평년값 (목업 자료)
    LocalDate far = today().plusDays(40);
    JsonNode c = api().get("/api/v1/weather/day?region=SEOUL&date=" + far, null).expect(200).body();
    assertThat(c.get("source").asString()).isEqualTo("CLIMATE");
    assertThat(c.get("day").get("cond").isNull()).isTrue();
    assertThat(c.get("day").get("condText").isNull()).isTrue();
    assertThat(c.get("day").get("min").asDouble()).isLessThan(c.get("day").get("max").asDouble());
    int lastYear = today().getYear() - 1;
    assertThat(c.get("climate").get("years").asString()).isEqualTo((lastYear - 9) + "-" + lastYear);
    assertThat(c.get("climate").get("rainDayRatio").asDouble()).isBetween(0.0, 1.0);
    assertThat(c.get("forecastUntil").asString()).isEqualTo(b.get("forecastUntil").asString());

    // 만료·엉터리 토큰이 같이 와도 region 방식은 토큰을 보지 않는다
    assertThat(
            api()
                .get("/api/v1/weather/day?region=SEOUL&date=" + today(), "expired.token.value")
                .status())
        .isEqualTo(200);
    // 지역이 없으면 서울
    assertThat(
            api()
                .get("/api/v1/weather/day?date=" + today(), null)
                .expect(200)
                .body()
                .get("place")
                .get("regionCode")
                .asString())
        .isEqualTo("SEOUL");
  }

  @Test
  void 좌표_방식과_장소_검색은_로그인이_필요하다() {
    coords(null, 35.68, 139.69, today()).expectError(401, "UNAUTHORIZED");
    // 좌표가 틀려도 로그인이 먼저
    coords(null, 999, 139.69, today()).expectError(401, "UNAUTHORIZED");
    api()
        .get("/api/v1/weather/day?lat=35.68&date=" + today(), null)
        .expectError(401, "UNAUTHORIZED");
    assertThat(coords("not.a.token", 35.68, 139.69, today()).status()).isEqualTo(401);
    api()
        .get("/api/v1/weather/places?q={q}", null, Map.of("q", "도쿄"))
        .expectError(401, "UNAUTHORIZED");
  }

  @Test
  void 날짜와_좌표를_검증한다() {
    String token = signup().accessToken();
    for (String q :
        new String[] {
          "region=SEOUL",
          "region=SEOUL&date=",
          "region=SEOUL&date=2026-13-01",
          "region=SEOUL&date=" + today().minusDays(1),
          "region=SEOUL&date=" + today().plusYears(1).plusDays(1)
        }) {
      Resp r = api().get("/api/v1/weather/day?" + q, null).expectError(400, "VALIDATION_FAILED");
      assertThat(r.body().get("fieldErrors").get(0).get("field").asString())
          .as(q)
          .isEqualTo("date");
    }
    assertThat(
            api()
                .get("/api/v1/weather/day?region=NOWHERE&date=" + today(), null)
                .expectError(400, "VALIDATION_FAILED")
                .body()
                .get("fieldErrors")
                .get(0)
                .get("field")
                .asString())
        .isEqualTo("region");
    Resp lat =
        api()
            .get("/api/v1/weather/day?lat=abc&lon=1&date=" + today(), token)
            .expectError(400, "VALIDATION_FAILED");
    assertThat(lat.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("lat");
    coords(token, 91, 10, today()).expectError(400, "VALIDATION_FAILED");
    api()
        .get("/api/v1/weather/day?region=SEOUL&lat=1&lon=2&date=" + today(), token)
        .expectError(400, "VALIDATION_FAILED");
  }

  @Test
  void 좌표_방식은_격자로_받아_캐시하고_동시에_와도_공급자는_한_번이다() throws Exception {
    double[] p = randomPlace();
    String gridKey = grid(p[0], p[1]);
    List<Session> users = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      users.add(signup());
    }
    ExecutorService pool = Executors.newFixedThreadPool(5);
    List<JsonNode> bodies = new ArrayList<>();
    try {
      List<Callable<Resp>> tasks = new ArrayList<>();
      for (Session u : users) {
        tasks.add(() -> coords(u.accessToken(), p[0], p[1], today()));
      }
      for (Future<Resp> f : pool.invokeAll(tasks)) {
        bodies.add(f.get().expect(200).body());
      }
    } finally {
      pool.shutdownNow();
    }
    for (JsonNode b : bodies) {
      assertThat(b.get("source").asString()).isEqualTo("FORECAST");
      assertThat(b.get("place").get("regionCode").isNull()).isTrue();
      assertThat(grid(b.get("place").get("lat").asDouble(), b.get("place").get("lon").asDouble()))
          .isEqualTo(gridKey);
      assertThat(LocalDate.parse(b.get("forecastUntil").asString())).isAfterOrEqualTo(today());
    }

    JsonNode calls = callsFor(gridKey);
    assertThat(calls.size()).isEqualTo(1);
    JsonNode call = calls.get(0);
    assertThat(call.get("callKind").asString()).isEqualTo("ON_DEMAND");
    assertThat(call.get("runId").isNull()).isTrue();
    assertThat(call.get("trigger").isNull()).isTrue();
    assertThat(call.get("providerCode").asString()).isEqualTo("WEATHERAPI");

    // 예보 밖 날짜는 그 격자의 평년값 (CLIMATE 호출은 OPEN_METEO로 남는다)
    JsonNode far =
        coords(users.getFirst().accessToken(), p[0], p[1], today().plusMonths(3))
            .expect(200)
            .body();
    assertThat(far.get("source").asString()).isEqualTo("CLIMATE");
    JsonNode after = callsFor(gridKey);
    assertThat(after.size()).isEqualTo(2);
    assertThat(after.get(0).get("callKind").asString()).isEqualTo("CLIMATE");
    assertThat(after.get(0).get("providerCode").asString()).isEqualTo("OPEN_METEO");
  }

  @Test
  void 회원_하루_한도를_넘으면_공급자를_부르지_않고_평년값으로_답한다() {
    String token = signup().accessToken();
    for (int i = 0; i < 3; i++) {
      double[] p = randomPlace();
      assertThat(coords(token, p[0], p[1], today()).expect(200).body().get("source").asString())
          .isEqualTo("FORECAST");
    }
    double[] p = randomPlace();
    JsonNode limited = coords(token, p[0], p[1], today()).expect(200).body();
    assertThat(limited.get("source").asString()).isEqualTo("CLIMATE");
    assertThat(limited.get("forecastUntil").isNull()).isTrue();
    JsonNode calls = callsFor(grid(p[0], p[1]));
    assertThat(calls.size()).isEqualTo(1);
    assertThat(calls.get(0).get("callKind").asString()).isEqualTo("CLIMATE");

    // 다른 회원은 그 격자를 받을 수 있다
    assertThat(
            coords(signup().accessToken(), p[0], p[1], today())
                .expect(200)
                .body()
                .get("source")
                .asString())
        .isEqualTo("FORECAST");
  }

  @Test
  void 장소를_검색하면_국내_결과에_REGION을_붙이고_호출_이력에_SEARCH로_남는다() {
    String token = signup().accessToken();
    JsonNode tokyo =
        api()
            .get("/api/v1/weather/places?q={q}", token, Map.of("q", " 도쿄 "))
            .expect(200)
            .body()
            .get("items");
    assertThat(tokyo.size()).isEqualTo(1);
    JsonNode t = tokyo.get(0);
    assertThat(t.get("label").asString()).isEqualTo("Tokyo");
    assertThat(t.get("country").asString()).isEqualTo("Japan");
    assertThat(t.get("lat").asDouble()).isEqualTo(35.69);
    assertThat(t.has("region")).isTrue();
    assertThat(t.get("regionCode").isNull()).isTrue();

    JsonNode busan =
        api()
            .get("/api/v1/weather/places?q={q}", token, Map.of("q", "busan"))
            .expect(200)
            .body()
            .get("items");
    assertThat(busan.get(0).get("regionCode").asString()).isEqualTo("BUSAN");

    for (String q : new String[] {"", "   ", "가".repeat(51)}) {
      Resp bad =
          api()
              .get("/api/v1/weather/places?q={q}", token, Map.of("q", q))
              .expectError(400, "VALIDATION_FAILED");
      assertThat(bad.body().get("fieldErrors").get(0).get("field").asString()).isEqualTo("q");
    }
    api().get("/api/v1/weather/places", token).expectError(400, "VALIDATION_FAILED");

    JsonNode calls =
        api()
            .get(
                "/api/v1/admin/weather/calls?providerCode=WEATHERAPI&size=100",
                admin().accessToken())
            .expect(200)
            .body()
            .get("items");
    boolean found = false;
    for (JsonNode c : calls) {
      if (c.get("callKind").asString().equals("SEARCH")) {
        assertThat(c.get("regionCode").isNull()).isTrue();
        assertThat(c.get("runId").isNull()).isTrue();
        found = true;
      }
    }
    assertThat(found).as("SEARCH 호출 이력").isTrue();
  }
}
