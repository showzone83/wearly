package app.wearly.weather.adapter.out.weather.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.support.FakeHttpServer;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.GeoPlace;
import app.wearly.weather.domain.GeocodeResult;
import app.wearly.weather.domain.PlaceCandidate;
import app.wearly.weather.domain.PlaceSearchRules;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 19 문서: Open-Meteo 지오코딩 API 요청 구성·응답 변환·오류 분류 (가짜 HTTP 서버). 응답 고정본은 2026-10-18에 실제
 * API(language=ko, count=10)를 불러 받은 도쿄·tokyo·오사카·강릉·강릉시·파리·해운대구(결과 없음)다.
 */
class OpenMeteoGeocodingClientTest {

  static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");
  static final String KEY = "om-geo-paid-key-0123456789";

  FakeHttpServer server;
  OpenMeteoGeocodingClient client;

  @BeforeEach
  void setUp() {
    server = new FakeHttpServer();
    client = client(null);
  }

  OpenMeteoGeocodingClient client(String key) {
    return new OpenMeteoGeocodingClient(
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

  static String fixture(String name) throws IOException {
    try (InputStream in =
        OpenMeteoGeocodingClientTest.class.getResourceAsStream(
            "/fixtures/weather/open-meteo-geocoding-" + name + ".json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  List<GeoPlace> places(String fixture) throws IOException {
    server.respond(200, fixture(fixture));
    GeocodeResult r = client.search("x");
    assertThat(r.ok()).isTrue();
    return r.places();
  }

  @Test
  void 한글_검색어로_불러_한글_이름으로_바꾼다() throws IOException {
    server.respond(200, fixture("tokyo-ko"));

    GeocodeResult r = client.search("도쿄");

    assertThat(r.ok()).isTrue();
    FakeHttpServer.Request req = server.last();
    assertThat(req.path()).isEqualTo("/v1/search");
    assertThat(URLDecoder.decode(req.query(), StandardCharsets.UTF_8))
        .contains("name=도쿄")
        .contains("language=ko")
        .contains("count=10")
        .contains("format=json")
        .doesNotContain("apikey");
    assertThat(r.places())
        .containsExactly(
            new GeoPlace("도쿄", "도쿄 도", "일본", "JP", "PPLC", 9733276L, 35.6895, 139.69171));
    assertThat(r.places().getFirst().toCandidate())
        .isEqualTo(new PlaceCandidate("도쿄", "도쿄 도", "일본", 35.6895, 139.69171));

    CallRecord call = r.calls().getFirst();
    assertThat(call.providerCode()).isEqualTo("OPEN_METEO_GEO");
    assertThat(call.callKind()).isEqualTo(CallRecord.SEARCH);
    assertThat(call.regionCode()).isNull();
    assertThat(call.success()).isTrue();
    assertThat(call.httpStatus()).isEqualTo(200);
    assertThat(call.responseBytes()).isPositive();
  }

  @Test
  void 고정본을_읽는다_인구가_없으면_null이고_결과가_없으면_빈_목록() throws IOException {
    assertThat(places("osaka"))
        .extracting(GeoPlace::name, GeoPlace::admin1, GeoPlace::featureCode, GeoPlace::population)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("오사카 시", "오사카 부", "PPLA", 2753862L),
            org.assertj.core.groups.Tuple.tuple("오사카 국제공항", "효고 현", "AIRP", null));
    assertThat(places("paris"))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.name()).isEqualTo("파리");
              assertThat(p.admin1()).isEqualTo("일드프랑스");
              assertThat(p.country()).isEqualTo("프랑스");
              assertThat(p.countryCode()).isEqualTo("FR");
            });
    assertThat(places("gangneung"))
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.name()).isEqualTo("강릉");
              assertThat(p.countryCode()).isEqualTo("KP");
              assertThat(p.korea()).isFalse();
            });
    assertThat(places("empty")).isEmpty();
  }

  @Test
  void 고정본으로_정렬과_국내_보정을_확인한다() throws IOException {
    // 강릉: 북한 마을만 나오므로 강릉시로 더 찾는다. 합치면 강원도 강릉시가 맨 위
    List<GeoPlace> gangneung = places("gangneung");
    assertThat(PlaceSearchRules.needsKoreanRetry("강릉", gangneung)).isTrue();
    assertThat(PlaceSearchRules.koreanVariants("강릉")).containsExactly("강릉시", "강릉군");
    List<GeoPlace> si = places("gangneung-si");
    assertThat(PlaceSearchRules.hasKoreaMajor(si)).isTrue();
    List<GeoPlace> all = new ArrayList<>(gangneung);
    all.addAll(si);
    assertThat(PlaceSearchRules.rank("강릉", all, 10))
        .extracting(PlaceCandidate::label, PlaceCandidate::region, PlaceCandidate::country)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("강릉시", "강원도", "대한민국"),
            org.assertj.core.groups.Tuple.tuple("강릉", "황해북도", "조선민주주의인민공화국"));

    // 도쿄·오사카·파리는 수도·1급 행정 중심이 있어 더 부르지 않는다
    assertThat(PlaceSearchRules.needsKoreanRetry("도쿄", places("tokyo-ko"))).isFalse();
    assertThat(PlaceSearchRules.needsKoreanRetry("오사카", places("osaka"))).isFalse();
    assertThat(PlaceSearchRules.needsKoreanRetry("파리", places("paris"))).isFalse();
    // 해운대구는 이미 '구'로 끝나 보정하지 않는다 (결과 없음 그대로)
    assertThat(PlaceSearchRules.needsKoreanRetry("해운대구", places("empty"))).isFalse();

    // 오사카: 도시가 공항보다 앞
    assertThat(PlaceSearchRules.rank("오사카", places("osaka"), 10))
        .extracting(PlaceCandidate::label)
        .containsExactly("오사카 시", "오사카 국제공항");

    // 영어 tokyo: 한국 마을(Tonggŏch’ado-ri)을 앞으로 올리지 않는다. 수도 → 도시·마을(인구 순) → 산·헬기장·공원
    List<PlaceCandidate> tokyo = PlaceSearchRules.rank("tokyo", places("tokyo-en"), 10);
    // Tokyo Heliport(35.63,139.85)와 Tokyo Disney Land(35.63,139.88)는 같은 격자(35.6,139.9)라 앞의 것만 남는다
    assertThat(tokyo).hasSize(9);
    assertThat(tokyo).extracting(PlaceCandidate::label).doesNotContain("Tokyo Disney Land");
    assertThat(tokyo.getFirst())
        .isEqualTo(new PlaceCandidate("도쿄", "도쿄 도", "일본", 35.6895, 139.69171));
    assertThat(tokyo.subList(1, 4))
        .extracting(PlaceCandidate::country)
        .containsExactlyInAnyOrder("파푸아뉴기니", "네팔", "대한민국");
    assertThat(tokyo.getLast().label()).isEqualTo("Tōkyōkō Yachō Kōen");
  }

  @Test
  void 오류_응답은_reason을_남기고_분류한다() {
    server.respond(
        400, "{\"reason\":\"Parameter count must be between 1 and 100.\",\"error\":true}");
    GeocodeResult bad = client.search("x");
    assertThat(bad.ok()).isFalse();
    assertThat(bad.errorCode()).isEqualTo(FetchErrors.HTTP_4XX);
    assertThat(bad.errorMessage()).contains("HTTP 400").contains("Parameter count");
    assertThat(bad.calls().getFirst().httpStatus()).isEqualTo(400);
    assertThat(bad.calls().getFirst().providerCode()).isEqualTo("OPEN_METEO_GEO");

    server.respond(429, "{\"reason\":\"Daily API request limit exceeded.\",\"error\":true}");
    assertThat(client.search("x").errorCode()).isEqualTo(FetchErrors.HTTP_429);

    server.respond(503, "<html>busy</html>");
    assertThat(client.search("x").errorCode()).isEqualTo(FetchErrors.HTTP_5XX);

    server.respond(200, "[1,2]");
    assertThat(client.search("x").errorCode()).isEqualTo(FetchErrors.PARSE);
  }

  @Test
  void 유료_키가_있으면_apikey로_보내고_오류_문구에서_지운다() {
    OpenMeteoGeocodingClient paid = client(KEY);
    server.respond(400, "{\"reason\":\"Invalid apikey " + KEY + "\",\"error\":true}");

    GeocodeResult r = paid.search("도쿄");

    assertThat(server.last().query()).contains("apikey=" + KEY);
    assertThat(r.errorMessage()).doesNotContain(KEY);
    assertThat(r.calls().getFirst().errorMessage()).doesNotContain(KEY);
  }

  @Test
  void 시간_초과는_TIMEOUT이다() {
    server.respond(200, "{}").delay(3000);

    GeocodeResult r = client.search("x");

    assertThat(r.errorCode()).isEqualTo(FetchErrors.TIMEOUT);
    assertThat(r.calls().getFirst().httpStatus()).isNull();
  }
}
