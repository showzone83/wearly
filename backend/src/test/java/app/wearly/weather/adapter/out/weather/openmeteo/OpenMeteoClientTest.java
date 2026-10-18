package app.wearly.weather.adapter.out.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.ClimateFetch;
import app.wearly.weather.domain.DailyObservation;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.MonthlyClimate;
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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 19 문서 4장: Open-Meteo 과거 기상 API 요청 구성·응답 변환·오류 분류 (가짜 HTTP 서버). 응답 고정본은 2026-10-18에 실제 API를 불러 받은
 * 서울 격자(37.6,127.0) 2024~2025년 자료다.
 */
class OpenMeteoClientTest {

  static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
  static final String KEY = "om-paid-key-0123456789";

  FakeHttpServer server;
  OpenMeteoClient client;

  @BeforeEach
  void setUp() {
    server = new FakeHttpServer();
    client = client(null);
  }

  OpenMeteoClient client(String key) {
    return new OpenMeteoClient(
        server.url(),
        key,
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
        OpenMeteoClientTest.class.getResourceAsStream(
            "/fixtures/weather/open-meteo-archive.json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void archive를_불러_일별_관측값으로_바꾼다() throws IOException {
    server.respond(200, fixture());

    ClimateFetch r =
        client.fetch(
            "37.6,127.0", 37.6, 127.0, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));

    assertThat(r.ok()).isTrue();
    FakeHttpServer.Request req = server.last();
    assertThat(req.path()).isEqualTo("/v1/archive");
    assertThat(URLDecoder.decode(req.query(), StandardCharsets.UTF_8))
        .contains("latitude=37.6")
        .contains("longitude=127.0")
        .contains("start_date=2024-01-01")
        .contains("end_date=2025-12-31")
        .contains("daily=temperature_2m_max,temperature_2m_min,precipitation_sum")
        .contains("timezone=auto")
        .doesNotContain("apikey");
    List<DailyObservation> days = r.days();
    assertThat(days).hasSize(731);
    assertThat(days.getFirst().date()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(days.getLast().date()).isEqualTo(LocalDate.of(2025, 12, 31));

    CallRecord call = r.calls().getFirst();
    assertThat(call.providerCode()).isEqualTo("OPEN_METEO");
    assertThat(call.callKind()).isEqualTo(CallRecord.CLIMATE);
    assertThat(call.regionCode()).isEqualTo("37.6,127.0");
    assertThat(call.success()).isTrue();
    assertThat(call.responseBytes()).isPositive();

    // 고정본으로 계산한 10월 평년값 (파이썬으로 따로 계산한 값과 같다)
    MonthlyClimate oct =
        MonthlyClimate.compute("37.6,127.0", days, "2024-2025", "OPEN_METEO", NOW).get(9);
    assertThat(oct.month()).isEqualTo(10);
    assertThat(oct.tempAvg()).isCloseTo(14.966, Offset.offset(0.001));
    assertThat(oct.tempMin()).isCloseTo(10.665, Offset.offset(0.001));
    assertThat(oct.tempMax()).isCloseTo(19.268, Offset.offset(0.001));
    assertThat(oct.rainDayRatio()).isCloseTo(19.0 / 62, Offset.offset(0.0001));
  }

  @Test
  void 자료가_없는_날은_null이다() {
    server.respond(
        200,
        "{\"daily\":{\"time\":[\"2024-01-01\",\"2024-01-02\"],"
            + "\"temperature_2m_max\":[1.5,null],\"temperature_2m_min\":[-3.0,null],"
            + "\"precipitation_sum\":[0.0,null]}}");

    ClimateFetch r = client.fetch("g", 1, 2, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

    assertThat(r.days())
        .containsExactly(
            new DailyObservation(LocalDate.of(2024, 1, 1), 1.5, -3.0, 0.0),
            new DailyObservation(LocalDate.of(2024, 1, 2), null, null, null));
  }

  @Test
  void 오류_응답은_reason을_남기고_분류한다() {
    server.respond(
        400,
        "{\"reason\":\"Latitude must be in range of -90 to 90°. Given: 137.6.\",\"error\":true}");
    ClimateFetch bad =
        client.fetch("g", 137.6, 127, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));
    assertThat(bad.ok()).isFalse();
    assertThat(bad.errorCode()).isEqualTo(FetchErrors.HTTP_4XX);
    assertThat(bad.errorMessage()).contains("HTTP 400").contains("Latitude must be in range");
    assertThat(bad.calls().getFirst().httpStatus()).isEqualTo(400);

    server.respond(429, "{\"reason\":\"Daily API request limit exceeded.\",\"error\":true}");
    assertThat(
            client.fetch("g", 1, 2, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)).errorCode())
        .isEqualTo(FetchErrors.HTTP_429);

    server.respond(200, "{\"latitude\":1}");
    assertThat(
            client.fetch("g", 1, 2, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)).errorCode())
        .isEqualTo(FetchErrors.PARSE);
  }

  @Test
  void 유료_키가_있으면_apikey로_보내고_오류_문구에서_지운다() {
    OpenMeteoClient paid = client(KEY);
    server.respond(400, "{\"reason\":\"Invalid apikey " + KEY + "\",\"error\":true}");

    ClimateFetch r = paid.fetch("g", 1, 2, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

    assertThat(server.last().query()).contains("apikey=" + KEY);
    assertThat(r.errorMessage()).doesNotContain(KEY);
    assertThat(r.calls().getFirst().errorMessage()).doesNotContain(KEY);
  }

  @Test
  void 시간_초과는_TIMEOUT이다() {
    server.respond(200, "{}").delay(3000);

    ClimateFetch r = client.fetch("g", 1, 2, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));

    assertThat(r.errorCode()).isEqualTo(FetchErrors.TIMEOUT);
    assertThat(r.calls().getFirst().httpStatus()).isNull();
  }
}
