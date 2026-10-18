package app.wearly.weather.adapter.out.weather.weatherapi;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherRegion;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 17 문서 4장: WeatherAPI.com 요청 구성·응답 변환·오류 분류·키 마스킹 (가짜 HTTP 서버) */
class WeatherApiClientTest {

  static final String KEY = "wapi-secret-key-0123456789";
  static final WeatherRegion SEOUL = new WeatherRegion("SEOUL", "서울", 37.5665, 126.978, true);
  static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

  FakeHttpServer server;
  WeatherApiClient client;

  @BeforeEach
  void setUp() {
    server = new FakeHttpServer();
    client =
        new WeatherApiClient(
            server.url() + "/v1",
            Duration.ofSeconds(2),
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  static String fixture() throws IOException {
    try (InputStream in =
        WeatherApiClientTest.class.getResourceAsStream(
            "/fixtures/weather/weatherapi-forecast.json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void forecast_json을_불러_공통_모양으로_바꾼다() throws IOException {
    server.respond(200, fixture());

    FetchResult r = client.fetch(KEY, SEOUL, 3);

    assertThat(r.ok()).isTrue();
    FakeHttpServer.Request req = server.last();
    assertThat(req.method()).isEqualTo("GET");
    assertThat(req.path()).isEqualTo("/v1/forecast.json");
    assertThat(URLDecoder.decode(req.query(), StandardCharsets.UTF_8))
        .contains("key=" + KEY)
        .contains("q=37.5665,126.9780")
        .contains("days=3")
        .contains("lang=ko")
        .contains("aqi=no")
        .contains("alerts=no");

    assertThat(r.report().locationName()).isEqualTo("Seoul");
    assertThat(r.report().current().temp()).isEqualTo(18.3);
    assertThat(r.report().current().feelsLike()).isEqualTo(17.64);
    assertThat(r.report().current().cond()).isEqualTo(WeatherCondition.SUNNY);
    assertThat(r.report().current().condText()).isEqualTo("맑음");
    assertThat(r.report().current().observedAt()).isEqualTo(Instant.ofEpochSecond(1792209300));

    List<DailyForecast> days = r.report().days();
    assertThat(days).hasSize(3);
    assertThat(days.get(0).date()).isEqualTo(LocalDate.parse("2026-10-17"));
    assertThat(days.get(0).min()).isEqualTo(10.2);
    assertThat(days.get(0).max()).isEqualTo(21.4);
    assertThat(days.get(0).temp()).isEqualTo(15.86);
    assertThat(days.get(1).cond()).isEqualTo(WeatherCondition.RAIN);
    assertThat(days.get(1).rainProb()).isEqualTo(87);
    assertThat(days.get(2).cond()).isEqualTo(WeatherCondition.CLOUDY);

    // 원본은 시간별 예보를 빼고 남긴다
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> raw =
        (List<Map<String, Object>>)
            ((Map<String, Object>) r.report().raw().get("forecast")).get("forecastday");
    assertThat(raw).hasSize(3).allSatisfy(d -> assertThat(d).doesNotContainKey("hour"));

    CallRecord call = r.calls().getFirst();
    assertThat(call.success()).isTrue();
    assertThat(call.httpStatus()).isEqualTo(200);
    assertThat(call.providerCode()).isEqualTo("WEATHERAPI");
    assertThat(call.regionCode()).isEqualTo("SEOUL");
    assertThat(call.responseBytes()).isPositive();
    assertThat(call.calledAt()).isEqualTo(NOW);
  }

  @Test
  void 인증_오류는_AUTH이고_공급자_문구와_키를_남기지_않는다() {
    server.respond(
        401,
        "{\"error\":{\"code\":2006,\"message\":\"API key provided is invalid: " + KEY + "\"}}");

    FetchResult r = client.fetch(KEY, SEOUL, 3);

    assertThat(r.ok()).isFalse();
    assertThat(r.errorCode()).isEqualTo(FetchErrors.AUTH);
    assertThat(r.errorMessage()).contains("HTTP 401").doesNotContain(KEY);
    assertThat(r.calls().getFirst().errorMessage()).doesNotContain(KEY);
    assertThat(r.calls().getFirst().httpStatus()).isEqualTo(401);
  }

  @Test
  void 월_한도_초과는_QUOTA이고_서버_오류는_HTTP_5XX다() {
    server.respond(403, "{\"error\":{\"code\":2007,\"message\":\"exceeded\"}}");
    assertThat(client.fetch(KEY, SEOUL, 3).errorCode()).isEqualTo(FetchErrors.QUOTA);

    server.respond(503, "{\"error\":{\"code\":9999,\"message\":\"Internal application error.\"}}");
    FetchResult r = client.fetch(KEY, SEOUL, 3);
    assertThat(r.errorCode()).isEqualTo(FetchErrors.HTTP_5XX);
    assertThat(r.errorMessage()).contains("HTTP 503").contains("Internal application error.");

    server.respond(429, "not json");
    assertThat(client.fetch(KEY, SEOUL, 3).errorCode()).isEqualTo(FetchErrors.HTTP_429);
  }

  @Test
  void 모양이_다른_응답은_PARSE다() {
    server.respond(200, "{\"location\":{}}");

    FetchResult r = client.fetch(KEY, SEOUL, 3);

    assertThat(r.errorCode()).isEqualTo(FetchErrors.PARSE);
    assertThat(r.calls().getFirst().httpStatus()).isEqualTo(200);
  }

  @Test
  void 시간_초과는_TIMEOUT이다() {
    server.respond(200, "{}").delay(3000);

    FetchResult r = client.fetch(KEY, SEOUL, 3);

    assertThat(r.errorCode()).isEqualTo(FetchErrors.TIMEOUT);
    assertThat(r.calls().getFirst().httpStatus()).isNull();
  }

  @Test
  void 연결_실패_문구에서도_키를_지운다() {
    WeatherApiClient down =
        new WeatherApiClient(
            "http://127.0.0.1:1/v1",
            Duration.ofSeconds(2),
            JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    FetchResult r = down.fetch(KEY, SEOUL, 3);

    assertThat(r.errorCode()).isEqualTo(FetchErrors.CLIENT);
    assertThat(r.errorMessage()).doesNotContain(KEY);
    assertThat(WeatherApiClient.scrub("GET http://x/forecast.json?key=" + KEY + "&q=1", null))
        .doesNotContain(KEY)
        .contains("key=***");
  }

  @Test
  void 날씨_코드를_앱_상태로_바꾼다() {
    assertThat(WeatherApiClient.condition(1000)).isEqualTo(WeatherCondition.SUNNY);
    for (int code : new int[] {1003, 1006, 1009, 1030, 1135, 1147}) {
      assertThat(WeatherApiClient.condition(code))
          .as("code %d", code)
          .isEqualTo(WeatherCondition.CLOUDY);
    }
    for (int code : new int[] {1063, 1066, 1087, 1183, 1195, 1213, 1240, 1276}) {
      assertThat(WeatherApiClient.condition(code))
          .as("code %d", code)
          .isEqualTo(WeatherCondition.RAIN);
    }
  }
}
