package app.wearly.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.CurrentWeather;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.GeoPlace;
import app.wearly.weather.domain.GeocodeResult;
import app.wearly.weather.domain.PlaceCandidate;
import app.wearly.weather.domain.PlaceSnapshot;
import app.wearly.weather.domain.PlaceWeatherOptions;
import app.wearly.weather.domain.ProviderHealth;
import app.wearly.weather.domain.SearchResult;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherReport;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 19 문서: 장소별 날씨 유스케이스 (가짜 포트). 순서 FORECAST → CLIMATE → 503, 좌표 캐시·TTL·격자 반올림, 회원 한도(429와 캐시 대체), 동시
 * 요청, 장소 검색(캐시·정리·REGION 붙이기·한도, 지오코딩·국내 지명 보정·실패 시 날씨 공급자 대체). 오늘은 한국 날짜 2026-10-17.
 */
class PlaceWeatherServiceTest {

  static final Instant NOW = WeatherFakes.NOW;
  static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  PlaceWeatherFakes fakes;
  WeatherFakes base;
  PlaceWeatherService service;

  @BeforeEach
  void setUp() {
    fakes = new PlaceWeatherFakes();
    base = fakes.base;
    base.provider("WEATHERAPI", 1, null);
    base.answers.put("WEATHERAPI", (p, r) -> threeDays(p, r));
    service = fakes.service();
  }

  /** 오늘부터 3일 예보 (17일 맑음 16°, 18일 비 14°, 19일 흐림 12°) */
  static FetchResult threeDays(WeatherProvider p, WeatherRegion r) {
    CurrentWeather cur = new CurrentWeather(18, 17.0, WeatherCondition.SUNNY, "맑음", NOW);
    List<DailyForecast> days =
        List.of(
            new DailyForecast(TODAY, 16, 10, 21, 10, WeatherCondition.SUNNY, "맑음"),
            new DailyForecast(TODAY.plusDays(1), 14, 11, 17, 80, WeatherCondition.RAIN, "비"),
            new DailyForecast(TODAY.plusDays(2), 12, 8, 15, 30, WeatherCondition.CLOUDY, "흐림"));
    return FetchResult.success(
        new WeatherReport("Tokyo", cur, days, Map.of()),
        List.of(WeatherFakes.call(p, r, true, 200, null, null)));
  }

  WeatherDayDto region(String code, LocalDate date) {
    return service.day(new WeatherDayQueryDto(code, null, null, date.toString(), null));
  }

  WeatherDayDto coords(long user, double lat, double lon, LocalDate date) {
    return service.day(new WeatherDayQueryDto(null, lat, lon, date.toString(), user));
  }

  static ApiException error(Runnable r) {
    try {
      r.run();
    } catch (ApiException e) {
      return e;
    }
    throw new AssertionError("ApiException이 나지 않았어요");
  }

  void regionSnapshot(String code) {
    WeatherRegion r = base.regions.stream().filter(x -> x.code().equals(code)).findFirst().get();
    FetchResult f = threeDays(base.providerRows.get("WEATHERAPI"), r);
    base.snapshotRows.put(
        code, WeatherSnapshot.of(code, "WEATHERAPI", f.report(), NOW.minusSeconds(600)));
  }

  PlaceSnapshot placeCache(double lat, double lon, Instant fetchedAt) {
    var grid = app.wearly.weather.domain.GridKey.of(lat, lon);
    FetchResult f =
        threeDays(
            base.providerRows.get("WEATHERAPI"),
            new WeatherRegion(grid.key(), "x", grid.lat(), grid.lon(), true));
    PlaceSnapshot s = PlaceSnapshot.of(grid, "Tokyo", "WEATHERAPI", f.report(), fetchedAt);
    fakes.placeRows.put(grid.key(), s);
    return s;
  }

  // ---------------------------------------------------------------- REGION

  @Test
  void REGION_예보에_날짜가_있으면_FORECAST이고_외부_호출이_없다() {
    regionSnapshot("SEOUL");

    WeatherDayDto d = region("SEOUL", TODAY.plusDays(1));

    assertThat(d.source()).isEqualTo("FORECAST");
    assertThat(d.place().regionCode()).isEqualTo("SEOUL");
    assertThat(d.place().label()).isEqualTo("서울");
    assertThat(d.place().lat()).isEqualTo(37.5665);
    assertThat(d.date()).isEqualTo(TODAY.plusDays(1));
    assertThat(d.day().temp()).isEqualTo(14);
    assertThat(d.day().min()).isEqualTo(11);
    assertThat(d.day().max()).isEqualTo(17);
    assertThat(d.day().rainProb()).isEqualTo(80);
    assertThat(d.day().cond()).isEqualTo("RAIN");
    assertThat(d.day().condText()).isEqualTo("비");
    assertThat(d.forecastUntil()).isEqualTo(TODAY.plusDays(2));
    assertThat(d.fetchedAt()).isEqualTo(NOW.minusSeconds(600));
    assertThat(d.climate()).isNull();
    assertThat(base.callRows).isEmpty();
    assertThat(fakes.climateFetches).isEmpty();
  }

  @Test
  void 예보_밖_날짜는_그_격자_그_달의_평년값이고_한_번만_계산한다() {
    regionSnapshot("SEOUL");

    WeatherDayDto d = region("SEOUL", LocalDate.of(2026, 10, 25));

    assertThat(d.source()).isEqualTo("CLIMATE");
    assertThat(d.day().temp()).isEqualTo(15);
    assertThat(d.day().min()).isEqualTo(10);
    assertThat(d.day().max()).isEqualTo(20);
    assertThat(d.day().rainProb()).isEqualTo(25);
    assertThat(d.day().cond()).isNull();
    assertThat(d.day().condText()).isNull();
    assertThat(d.climate().years()).isEqualTo("2016-2025");
    assertThat(d.climate().rainDayRatio()).isEqualTo(0.25);
    assertThat(d.forecastUntil()).isEqualTo(TODAY.plusDays(2));
    assertThat(d.fetchedAt()).isEqualTo(NOW);
    // 서울 좌표(37.5665, 126.978)의 격자
    assertThat(fakes.climateFetches).containsExactly("37.6,127.0@2016-01-01~2025-12-31");
    assertThat(base.callRows)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.callKind()).isEqualTo(CallRecord.CLIMATE);
              assertThat(c.providerCode()).isEqualTo("OPEN_METEO");
              assertThat(c.regionCode()).isEqualTo("37.6,127.0");
            });
    assertThat(base.callRunIds).containsOnlyNulls();

    // 같은 격자의 다른 달(1월)도 이미 계산해 두었다
    WeatherDayDto jan = region("SEOUL", LocalDate.of(2027, 1, 5));
    assertThat(jan.day().max()).isEqualTo(2);
    assertThat(fakes.climateFetches).hasSize(1);
  }

  @Test
  void 예보도_평년값도_없으면_503이고_지역이_없으면_서울이다() {
    fakes.climateAnswer = fakes::climateFailed;

    assertThat(error(() -> region(null, TODAY)).code()).isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(fakes.climateFetches).containsExactly("37.6,127.0@2016-01-01~2025-12-31");

    regionSnapshot("SEOUL");
    assertThat(region(" ", TODAY).place().regionCode()).isEqualTo("SEOUL");

    ApiException bad = error(() -> region("NOWHERE", TODAY));
    assertThat(bad.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(bad.fieldErrors()).extracting(FieldError::field).containsExactly("region");
  }

  @Test
  void 평년값이_꺼져_있으면_CLIMATE가_없다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 100, false, 10, Duration.ofSeconds(2));
    service = fakes.service();
    regionSnapshot("SEOUL");

    assertThat(error(() -> region("SEOUL", TODAY.plusDays(10))).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(fakes.climateFetches).isEmpty();
  }

  // ---------------------------------------------------------------- 날짜·좌표 검증

  @Test
  void 날짜는_오늘부터_1년_안의_YYYY_MM_DD다() {
    regionSnapshot("SEOUL");
    for (String bad : new String[] {null, "", "2026/10/20", "2026-10-16", "2027-10-18", "x"}) {
      ApiException e =
          error(() -> service.day(new WeatherDayQueryDto("SEOUL", null, null, bad, null)));
      assertThat(e.code()).as(bad).isEqualTo(ErrorCode.VALIDATION_FAILED);
      assertThat(e.fieldErrors()).extracting(FieldError::field).containsExactly("date");
    }
    assertThat(region("SEOUL", TODAY).source()).isEqualTo("FORECAST");
    assertThat(region("SEOUL", TODAY.plusYears(1)).source()).isEqualTo("CLIMATE");

    // 날짜와 지역이 둘 다 틀리면 둘 다 알려 준다
    ApiException both =
        error(() -> service.day(new WeatherDayQueryDto("NOWHERE", null, null, "x", null)));
    assertThat(both.fieldErrors()).extracting(FieldError::field).containsExactly("date", "region");
  }

  @Test
  void 좌표_방식은_로그인이_먼저이고_좌표를_검증한다() {
    assertThat(
            error(() -> service.day(new WeatherDayQueryDto(null, 999.0, null, "x", null))).code())
        .isEqualTo(ErrorCode.UNAUTHORIZED);

    ApiException onlyLat =
        error(() -> service.day(new WeatherDayQueryDto(null, 35.0, null, TODAY.toString(), 1L)));
    assertThat(onlyLat.fieldErrors()).extracting(FieldError::field).containsExactly("lon");

    ApiException range =
        error(() -> service.day(new WeatherDayQueryDto(null, 91.0, 181.0, TODAY.toString(), 1L)));
    assertThat(range.fieldErrors()).extracting(FieldError::field).containsExactly("lat", "lon");

    ApiException both =
        error(
            () -> service.day(new WeatherDayQueryDto("SEOUL", 35.0, 139.0, TODAY.toString(), 1L)));
    assertThat(both.fieldErrors()).extracting(FieldError::field).containsExactly("region");
    assertThat(base.callRows).isEmpty();
  }

  // ---------------------------------------------------------------- 좌표 캐시

  @Test
  void 좌표는_격자로_반올림해_한_번_받고_TTL_안에서는_다시_부르지_않는다() {
    WeatherDayDto d = coords(1, 35.6895, 139.6917, TODAY.plusDays(1));

    assertThat(d.source()).isEqualTo("FORECAST");
    assertThat(d.place().regionCode()).isNull();
    assertThat(d.place().label()).isEqualTo("Tokyo");
    assertThat(d.place().lat()).isEqualTo(35.7);
    assertThat(d.place().lon()).isEqualTo(139.7);
    assertThat(d.day().cond()).isEqualTo("RAIN");
    assertThat(d.forecastUntil()).isEqualTo(TODAY.plusDays(2));
    assertThat(d.fetchedAt()).isEqualTo(NOW);
    assertThat(base.fetched).containsExactly("WEATHERAPI:35.7,139.7");
    assertThat(base.callRows)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.callKind()).isEqualTo(CallRecord.ON_DEMAND);
              assertThat(c.regionCode()).isEqualTo("35.7,139.7");
            });
    assertThat(base.callRunIds).containsOnlyNulls();
    assertThat(fakes.placeRows).containsOnlyKeys("35.7,139.7");

    // 가까운 좌표는 같은 격자 → 캐시
    coords(2, 35.71, 139.66, TODAY);
    assertThat(base.fetched).hasSize(1);
    assertThat(fakes.usage).containsOnlyKeys("ON_DEMAND:2026-10-17:1");
  }

  @Test
  void 캐시가_TTL보다_오래되면_다시_받는다() {
    placeCache(35.7, 139.7, NOW.minus(Duration.ofHours(2)));
    coords(1, 35.7, 139.7, TODAY);
    assertThat(base.fetched).isEmpty();

    placeCache(35.7, 139.7, NOW.minus(Duration.ofHours(4)));
    coords(1, 35.7, 139.7, TODAY);
    assertThat(base.fetched).containsExactly("WEATHERAPI:35.7,139.7");
    assertThat(fakes.placeRows.get("35.7,139.7").fetchedAt()).isEqualTo(NOW);
  }

  @Test
  void 예보에_없는_날짜는_그_좌표의_평년값이다() {
    WeatherDayDto d = coords(1, 35.6895, 139.6917, LocalDate.of(2026, 10, 30));

    assertThat(d.source()).isEqualTo("CLIMATE");
    assertThat(d.place().label()).isEqualTo("Tokyo");
    assertThat(d.forecastUntil()).isEqualTo(TODAY.plusDays(2));
    assertThat(fakes.climateFetches).containsExactly("35.7,139.7@2016-01-01~2025-12-31");
  }

  @Test
  void 회원_하루_한도를_넘으면_예전_캐시_그다음_평년값_그것도_없으면_429다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 1, Duration.ofDays(1), 100, true, 10, Duration.ofSeconds(2));
    service = fakes.service();
    coords(1, 10, 10, TODAY); // 한도 1회 사용
    assertThat(base.fetched).hasSize(1);

    // 오래된 캐시가 있으면 그 예보
    placeCache(20, 20, NOW.minus(Duration.ofHours(5)));
    WeatherDayDto stale = coords(1, 20, 20, TODAY.plusDays(1));
    assertThat(stale.source()).isEqualTo("FORECAST");
    assertThat(stale.fetchedAt()).isEqualTo(NOW.minus(Duration.ofHours(5)));

    // 캐시가 없으면 평년값
    WeatherDayDto climate = coords(1, 30, 30, TODAY);
    assertThat(climate.source()).isEqualTo("CLIMATE");
    assertThat(climate.forecastUntil()).isNull();
    assertThat(climate.place().label()).isEqualTo("30.0,30.0");

    // 평년값도 없으면 429
    fakes.climateAnswer = fakes::climateFailed;
    assertThat(error(() -> coords(1, 40, 40, TODAY)).code()).isEqualTo(ErrorCode.WEATHER_LIMIT);
    assertThat(base.fetched).hasSize(1);

    // 다른 회원은 부를 수 있다
    coords(2, 40, 40, TODAY);
    assertThat(base.fetched).hasSize(2);
  }

  @Test
  void 공급자가_차단_중이면_부르지_않고_한도도_세지_않는다() {
    WeatherProvider p = base.providerRows.get("WEATHERAPI");
    base.providerRows.put(
        "WEATHERAPI",
        p.withHealth(
            new ProviderHealth(
                3, ProviderHealth.FAILURES, NOW.plusSeconds(600), null, NOW, "HTTP_5XX")));

    WeatherDayDto d = coords(1, 10, 10, TODAY);

    assertThat(d.source()).isEqualTo("CLIMATE");
    assertThat(base.fetched).isEmpty();
    assertThat(fakes.usage).isEmpty();

    fakes.climateAnswer = fakes::climateFailed;
    assertThat(error(() -> coords(1, 11, 11, TODAY)).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
  }

  @Test
  void 받기에_실패하면_오류를_남기고_평년값으로_답한다() {
    base.answers.put("WEATHERAPI", WeatherFakes.failing(base, FetchErrors.HTTP_5XX, 503));

    WeatherDayDto d = coords(1, 10, 10, TODAY);

    assertThat(d.source()).isEqualTo("CLIMATE");
    assertThat(fakes.placeRows.get("10.0,10.0").lastError()).contains("HTTP_5XX");
    assertThat(fakes.placeRows.get("10.0,10.0").hasData()).isFalse();
    assertThat(base.callRows.stream().filter(c -> !c.success()).map(CallRecord::callKind))
        .containsExactly(CallRecord.ON_DEMAND);
    assertThat(base.providerRows.get("WEATHERAPI").health().consecutiveFailures()).isEqualTo(1);
  }

  @Test
  void 같은_격자를_동시에_요청해도_공급자는_한_번만_부른다() throws Exception {
    base.answers.put(
        "WEATHERAPI",
        (p, r) -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return threeDays(p, r);
        });
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Callable<WeatherDayDto>> tasks = new ArrayList<>();
      IntStream.range(0, 6)
          .forEach(i -> tasks.add(() -> coords(i + 1, 35.6895, 139.6917, TODAY.plusDays(1))));
      List<Future<WeatherDayDto>> results = pool.invokeAll(tasks);
      for (Future<WeatherDayDto> f : results) {
        assertThat(f.get().source()).isEqualTo("FORECAST");
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(fakes.onDemandCalls()).isEqualTo(1);
    assertThat(fakes.lockRows).isEmpty();
  }

  // ---------------------------------------------------------------- 장소 검색

  @Test
  void 검색어를_정리해_한_번_부르고_캐시하며_가까운_REGION을_붙인다() {
    base.searchAnswers.put(
        "WEATHERAPI",
        (p, q) ->
            SearchResult.success(
                List.of(
                    new PlaceCandidate("Busan", "", "South Korea", 35.1, 129.04),
                    new PlaceCandidate("Tokyo", "Tokyo", "Japan", 35.69, 139.69)),
                List.of(
                    new CallRecord(
                        null,
                        "WEATHERAPI",
                        CallRecord.FORECAST,
                        false,
                        200,
                        true,
                        null,
                        null,
                        5,
                        5,
                        NOW))));

    List<PlaceCandidateDto> items = service.searchPlaces(1, "  Busan   TOKYO ");

    assertThat(base.searched).containsExactly("WEATHERAPI:busan tokyo");
    assertThat(items)
        .containsExactly(
            new PlaceCandidateDto("Busan", "", "South Korea", 35.1, 129.04, "BUSAN"),
            new PlaceCandidateDto("Tokyo", "Tokyo", "Japan", 35.69, 139.69, null));
    assertThat(base.callRows)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.callKind()).isEqualTo(CallRecord.SEARCH);
              assertThat(c.regionCode()).isNull();
            });
    assertThat(base.callRunIds).containsOnlyNulls();

    // 같은 검색어(대소문자·공백만 다름)는 캐시
    assertThat(service.searchPlaces(2, "busan tokyo")).isEqualTo(items);
    assertThat(base.searched).hasSize(1);
    assertThat(fakes.usage).containsOnlyKeys("SEARCH:2026-10-17:1");
  }

  @Test
  void 검색_결과는_10개까지이고_빈_결과도_캐시한다() {
    base.searchAnswers.put(
        "WEATHERAPI",
        (p, q) ->
            SearchResult.success(
                IntStream.range(0, 15)
                    .mapToObj(i -> new PlaceCandidate("P" + i, "", "X", i, i))
                    .toList(),
                List.of()));
    assertThat(service.searchPlaces(1, "p")).hasSize(10);

    base.searchAnswers.remove("WEATHERAPI");
    assertThat(service.searchPlaces(1, "없는곳")).isEmpty();
    assertThat(service.searchPlaces(1, "없는곳")).isEmpty();
    assertThat(base.searched).hasSize(2);
  }

  @Test
  void 검색어는_1자에서_50자다() {
    for (String bad : new String[] {null, "", "   ", "가".repeat(51)}) {
      ApiException e = error(() -> service.searchPlaces(1, bad));
      assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
      assertThat(e.fieldErrors()).extracting(FieldError::field).containsExactly("q");
    }
    service.searchPlaces(1, "가".repeat(50));
    assertThat(base.searched).hasSize(1);
  }

  @Test
  void 검색_한도를_넘으면_429이고_캐시된_검색어는_된다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 1, true, 10, Duration.ofSeconds(2));
    service = fakes.service();
    service.searchPlaces(1, "tokyo");

    ApiException e = error(() -> service.searchPlaces(1, "osaka"));
    assertThat(e.code()).isEqualTo(ErrorCode.WEATHER_LIMIT);
    assertThat(e.getMessage()).contains("검색");
    assertThat(service.searchPlaces(1, "tokyo")).isEmpty();
    assertThat(base.searched).containsExactly("WEATHERAPI:tokyo");
  }

  @Test
  void 검색할_공급자가_없거나_실패하면_503이고_캐시하지_않는다() {
    base.searchAnswers.put(
        "WEATHERAPI",
        (p, q) ->
            SearchResult.failed(
                FetchErrors.HTTP_5XX,
                "WeatherAPI API 오류 (HTTP 502)",
                List.of(
                    new CallRecord(
                        null,
                        "WEATHERAPI",
                        CallRecord.SEARCH,
                        false,
                        502,
                        false,
                        "HTTP_5XX",
                        "x",
                        5,
                        5,
                        NOW))));
    assertThat(error(() -> service.searchPlaces(1, "tokyo")).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(fakes.searchRows).isEmpty();

    // 검색을 지원하는 공급자가 없으면 부르지 않고 한도도 세지 않는다
    PlaceWeatherFakes other = new PlaceWeatherFakes();
    other.base.provider("NOSEARCH", 1, null);
    assertThat(error(() -> other.service().searchPlaces(1, "tokyo")).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(other.base.searched).isEmpty();
    assertThat(other.usage).isEmpty();
  }

  // ---------------------------------------------------------------- 장소 검색 (지오코딩)

  // 2026-10-18 실제 Open-Meteo 지오코딩 응답의 값 (adapter 테스트의 고정본과 같다)
  static final GeoPlace TOKYO =
      new GeoPlace("도쿄", "도쿄 도", "일본", "JP", "PPLC", 9733276L, 35.6895, 139.69171);
  static final GeoPlace GANGNEUNG_KP =
      new GeoPlace("강릉", "황해북도", "조선민주주의인민공화국", "KP", "PPL", null, 38.27573, 126.52414);
  static final GeoPlace GANGNEUNG_SI =
      new GeoPlace("강릉시", "강원도", "대한민국", "KR", "PPLA3", 208161L, 37.75266, 128.87239);
  static final GeoPlace BUSAN_KR =
      new GeoPlace("부산", "부산광역시", "대한민국", "KR", "PPLA", 3678555L, 35.10168, 129.03004);

  /** 검색어별 지오코딩 답 (없는 검색어는 결과 없음) */
  void geo(Map<String, List<GeoPlace>> answers) {
    fakes.geoEnabled = true;
    fakes.geoAnswer = q -> PlaceWeatherFakes.geoOk(answers.getOrDefault(q, List.of()));
    service = fakes.service();
  }

  List<CallRecord> callsOf(String provider) {
    return base.callRows.stream().filter(c -> c.providerCode().equals(provider)).toList();
  }

  @Test
  void 지오코딩으로_찾아_한글_이름을_주고_SEARCH로_남기며_캐시한다() {
    geo(Map.of("도쿄", List.of(TOKYO)));

    List<PlaceCandidateDto> items = service.searchPlaces(1, " 도쿄 ");

    assertThat(items)
        .containsExactly(new PlaceCandidateDto("도쿄", "도쿄 도", "일본", 35.6895, 139.69171, null));
    // 도쿄는 수도(PPLC)라 국내 보정 검색을 하지 않는다. 날씨 공급자 검색도 부르지 않는다
    assertThat(fakes.geocoded).containsExactly("도쿄");
    assertThat(base.searched).isEmpty();
    assertThat(base.callRows)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.providerCode()).isEqualTo("OPEN_METEO_GEO");
              assertThat(c.callKind()).isEqualTo(CallRecord.SEARCH);
              assertThat(c.regionCode()).isNull();
            });
    assertThat(base.callRunIds).containsOnlyNulls();

    assertThat(service.searchPlaces(2, "도쿄")).isEqualTo(items);
    assertThat(fakes.geocoded).hasSize(1);
    assertThat(fakes.usage).containsOnlyKeys("SEARCH:2026-10-17:1");
    // 지오코딩 호출은 날씨 공급자(WEATHERAPI) 월 사용량에 들어가지 않는다
    assertThat(base.runner().monthUsage()).doesNotContainKey("WEATHERAPI");
  }

  @Test
  void 한글_국내_지명은_시를_붙여_더_찾고_한국_결과를_맨_위에_둔다() {
    geo(Map.of("강릉", List.of(GANGNEUNG_KP), "강릉시", List.of(GANGNEUNG_SI)));

    List<PlaceCandidateDto> items = service.searchPlaces(1, "강릉");

    assertThat(items)
        .extracting(PlaceCandidateDto::label, PlaceCandidateDto::region, PlaceCandidateDto::country)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("강릉시", "강원도", "대한민국"),
            org.assertj.core.groups.Tuple.tuple("강릉", "황해북도", "조선민주주의인민공화국"));
    // 강릉시에서 한국 결과가 나와 강릉군은 찾지 않는다
    assertThat(fakes.geocoded).containsExactly("강릉", "강릉시");
    assertThat(callsOf("OPEN_METEO_GEO")).hasSize(2).allMatch(c -> c.callKind().equals("SEARCH"));
    // 보정 검색까지 한 요청으로 센다
    assertThat(fakes.usage).containsEntry("SEARCH:2026-10-17:1", 1);
    assertThat(fakes.searchRows.get("강릉")).hasSize(2);
  }

  @Test
  void 시로도_한국이_없으면_군까지_찾는다() {
    GeoPlace yangpyeong = new GeoPlace("양평군", "경기도", "대한민국", "KR", "ADM2", 120000L, 37.49, 127.49);
    // 양평시에서 인구 없는 한국 마을만 나오면 아직 멈추지 않는다
    GeoPlace village = new GeoPlace("양평시", "전라남도", "대한민국", "KR", "PPL", null, 34.9, 126.9);
    geo(Map.of("양평시", List.of(village), "양평군", List.of(yangpyeong)));

    assertThat(service.searchPlaces(1, "양평"))
        .extracting(PlaceCandidateDto::label, PlaceCandidateDto::regionCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("양평군", null),
            org.assertj.core.groups.Tuple.tuple("양평시", null));
    assertThat(fakes.geocoded).containsExactly("양평", "양평시", "양평군");
  }

  @Test
  void 영어_검색어_한국_결과가_있는_검색어_구로_끝나는_검색어는_보정하지_않는다() {
    geo(Map.of("부산", List.of(BUSAN_KR), "busan", List.of(BUSAN_KR)));

    assertThat(service.searchPlaces(1, "부산"))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.label()).isEqualTo("부산");
              assertThat(c.regionCode()).isEqualTo("BUSAN");
            });
    service.searchPlaces(1, "Busan");
    assertThat(service.searchPlaces(1, "해운대구")).isEmpty();
    assertThat(fakes.geocoded).containsExactly("부산", "busan", "해운대구");
  }

  @Test
  void 정렬은_한국_행정중심_도시_시설_인구순이고_같은_격자는_하나만_10개까지() {
    List<GeoPlace> answer = new ArrayList<>();
    answer.add(new GeoPlace("공원", "x", "일본", "JP", "PRK", null, 10.0, 10.0));
    answer.add(new GeoPlace("작은 마을", "x", "일본", "JP", "PPL", 100L, 11.0, 11.0));
    answer.add(new GeoPlace("큰 마을", "x", "일본", "JP", "PPL", 5000L, 12.0, 12.0));
    answer.add(new GeoPlace("공항", "x", "일본", "JP", "AIRP", null, 13.0, 13.0));
    answer.add(new GeoPlace("현청", "x", "일본", "JP", "PPLA", 10L, 14.0, 14.0));
    answer.add(new GeoPlace("북한 도시", "x", "조선", "KP", "PPLA", 900000L, 15.0, 15.0));
    // 인구가 있는 한국 마을 (행정 중심·인구 있는 한국 결과가 맨 앞)
    answer.add(new GeoPlace("한국 마을", null, "대한민국", "KR", "PPL", 50L, 16.0, 16.0));
    // 같은 격자(16.0,16.0)의 뒤 항목은 버린다
    answer.add(new GeoPlace("한국 마을 옆", "x", "대한민국", "KR", "PPL", null, 16.04, 15.96));
    for (int i = 0; i < 10; i++) {
      answer.add(new GeoPlace("기타" + i, "x", "일본", "JP", "MT", null, 20.0 + i, 20.0));
    }
    geo(Map.of("가나다", answer));

    List<PlaceCandidateDto> items = service.searchPlaces(1, "가나다");

    assertThat(items).hasSize(10);
    assertThat(items)
        .extracting(PlaceCandidateDto::label)
        // 한국의 행정 중심·인구 있는 곳 → (북한 포함 나머지) 행정 중심 → 도시·마을 → 시설·지형, 같은 순위 안에서는 인구 많은 순
        .containsExactly("한국 마을", "북한 도시", "현청", "큰 마을", "작은 마을", "공원", "공항", "기타0", "기타1", "기타2")
        .doesNotContain("한국 마을 옆");
    assertThat(items.getFirst().region()).isEmpty();
    // 한국 결과가 있어 보정 검색은 하지 않는다
    assertThat(fakes.geocoded).containsExactly("가나다");
  }

  @Test
  void 지오코딩이_실패하면_날씨_공급자_검색으로_대신하고_캐시하지_않는다() {
    fakes.geoEnabled = true;
    fakes.geoAnswer = q -> PlaceWeatherFakes.geoFailed();
    service = fakes.service();
    base.searchAnswers.put(
        "WEATHERAPI",
        (p, q) ->
            SearchResult.success(
                List.of(new PlaceCandidate("Tokyo", "Tokyo", "Japan", 35.69, 139.69)),
                List.of(
                    new CallRecord(
                        null,
                        "WEATHERAPI",
                        CallRecord.SEARCH,
                        false,
                        200,
                        true,
                        null,
                        null,
                        5,
                        5,
                        NOW))));

    assertThat(service.searchPlaces(1, "tokyo"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("Tokyo");
    assertThat(base.searched).containsExactly("WEATHERAPI:tokyo");
    assertThat(callsOf("OPEN_METEO_GEO"))
        .singleElement()
        .satisfies(c -> assertThat(c.success()).isFalse());
    assertThat(callsOf("WEATHERAPI")).hasSize(1);
    assertThat(fakes.searchRows).isEmpty();
    assertThat(fakes.usage).containsEntry("SEARCH:2026-10-17:1", 1);

    // 지오코딩이 예외를 던져도 같은 방식으로 대신한다
    fakes.geoAnswer =
        q -> {
          throw new IllegalStateException("boom");
        };
    service = fakes.service();
    assertThat(service.searchPlaces(1, "tokyo")).hasSize(1);
    assertThat(callsOf("OPEN_METEO_GEO")).hasSize(2);
    assertThat(callsOf("OPEN_METEO_GEO").getLast().errorCode()).isEqualTo(FetchErrors.CLIENT);
  }

  @Test
  void 지오코딩도_날씨_공급자도_안되면_503이고_보정_호출_실패는_첫_결과를_쓴다() {
    PlaceWeatherFakes other = new PlaceWeatherFakes();
    other.base.provider("NOSEARCH", 1, null);
    other.geoEnabled = true;
    other.geoAnswer = q -> PlaceWeatherFakes.geoFailed();
    assertThat(error(() -> other.service().searchPlaces(1, "tokyo")).code())
        .isEqualTo(ErrorCode.WEATHER_UNAVAILABLE);
    assertThat(other.searchRows).isEmpty();

    // 날씨 공급자에 검색이 없어도 지오코딩이 되면 검색된다
    Function<String, GeocodeResult> answer =
        q ->
            q.equals("강릉")
                ? PlaceWeatherFakes.geoOk(List.of(GANGNEUNG_KP))
                : PlaceWeatherFakes.geoFailed();
    other.geoAnswer = answer;
    assertThat(other.service().searchPlaces(1, "강릉"))
        .extracting(PlaceCandidateDto::label)
        .containsExactly("강릉");
    // 강릉시가 실패하면 강릉군은 부르지 않고 첫 결과를 캐시한다
    assertThat(other.geocoded).containsExactly("tokyo", "강릉", "강릉시");
    assertThat(other.searchRows).containsOnlyKeys("강릉");
  }

  @Test
  void 지오코딩_검색도_회원_하루_한도를_따른다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 1, true, 10, Duration.ofSeconds(2));
    geo(Map.of("강릉", List.of(GANGNEUNG_KP), "강릉시", List.of(GANGNEUNG_SI)));
    service.searchPlaces(1, "강릉");

    ApiException e = error(() -> service.searchPlaces(1, "도쿄"));
    assertThat(e.code()).isEqualTo(ErrorCode.WEATHER_LIMIT);
    assertThat(service.searchPlaces(1, "강릉")).hasSize(2);
    assertThat(fakes.geocoded).containsExactly("강릉", "강릉시");
  }

  // ---------------------------------------------------------------- 장소 검색 (시·도 이름)

  /** 시·도 17개로 바꾸고 지오코딩 답을 고정본으로 */
  void regionsAndFixtures(Map<String, String> fixtures) {
    base.regions = PlaceWeatherFakes.allRegions();
    fakes.geoEnabled = true;
    fakes.geoAnswer =
        q ->
            PlaceWeatherFakes.geoOk(
                fixtures.containsKey(q)
                    ? PlaceWeatherFakes.geoFixture(fixtures.get(q))
                    : List.of());
    service = fakes.service();
  }

  @Test
  void 부산은_시도_부산이_맨_앞이고_로마자_Pusan은_빼고_작은_마을에는_지역_코드를_붙이지_않는다() {
    regionsAndFixtures(Map.of("부산", "busan"));

    List<PlaceCandidateDto> items = service.searchPlaces(1, "부산");

    assertThat(items.getFirst())
        .isEqualTo(new PlaceCandidateDto("부산", "부산광역시", "대한민국", 35.1796, 129.0756, "BUSAN"));
    // 실제 응답: Pusan(경북)·Pusan(경남)·부산(전남 마을)·부산(충북 산)
    assertThat(items).extracting(PlaceCandidateDto::label).doesNotContain("Pusan");
    assertThat(items)
        .extracting(
            PlaceCandidateDto::label, PlaceCandidateDto::region, PlaceCandidateDto::regionCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("부산", "부산광역시", "BUSAN"),
            // 광주(GWANGJU)에서 약 18km지만 인구 없는 마을이라 코드를 붙이지 않는다
            org.assertj.core.groups.Tuple.tuple("부산", "전라남도", null),
            org.assertj.core.groups.Tuple.tuple("부산", "충청북도", null));
    // 시·도와 맞았으므로 부산시·부산군 보정 검색은 하지 않는다
    assertThat(fakes.geocoded).containsExactly("부산");
  }

  @Test
  void 서울_제주_대구_인천은_그_시도가_맨_앞이다() {
    regionsAndFixtures(Map.of("서울", "seoul", "대구", "daegu", "인천", "incheon"));

    assertThat(service.searchPlaces(1, "서울"))
        .singleElement()
        .isEqualTo(new PlaceCandidateDto("서울", "서울특별시", "대한민국", 37.5665, 126.978, "SEOUL"));
    assertThat(service.searchPlaces(1, "제주"))
        .singleElement()
        .isEqualTo(new PlaceCandidateDto("제주", "제주특별자치도", "대한민국", 33.4996, 126.5312, "JEJU"));

    List<PlaceCandidateDto> daegu = service.searchPlaces(1, "대구");
    assertThat(daegu.getFirst().regionCode()).isEqualTo("DAEGU");
    assertThat(daegu.getFirst().label()).isEqualTo("대구");
    // 전남의 대구 마을 둘(한국) → 북한 강원도의 대구, 모두 지역 코드 없음
    assertThat(daegu.subList(1, daegu.size()))
        .extracting(PlaceCandidateDto::region, PlaceCandidateDto::regionCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("전라남도", null),
            org.assertj.core.groups.Tuple.tuple("전라남도", null),
            org.assertj.core.groups.Tuple.tuple("강원도", null));

    List<PlaceCandidateDto> incheon = service.searchPlaces(1, "인천");
    assertThat(incheon.getFirst().regionCode()).isEqualTo("INCHEON");
    assertThat(incheon).hasSize(4);
    assertThat(incheon.subList(1, 4)).allMatch(c -> c.regionCode() == null);
    assertThat(fakes.geocoded).containsExactly("서울", "제주", "대구", "인천");
  }

  @Test
  void 정식_이름과_앞부분도_맞추고_같은_곳의_행정_중심은_뺀다() {
    regionsAndFixtures(Map.of("부산광", "busan-metro", "부산광역시", "busan-metro"));

    // 부산광역시(PPLA, 인구 3285147)는 시·도 부산과 30km 안이라 한 번만
    assertThat(service.searchPlaces(1, "부산광"))
        .singleElement()
        .satisfies(c -> assertThat(c.regionCode()).isEqualTo("BUSAN"));
    assertThat(service.searchPlaces(1, "부산광역시")).hasSize(1);
    assertThat(service.searchPlaces(1, "충청"))
        .extracting(PlaceCandidateDto::regionCode)
        .containsExactly("CHUNGBUK", "CHUNGNAM");
    assertThat(service.searchPlaces(1, "전라북도"))
        .extracting(PlaceCandidateDto::regionCode)
        .containsExactly("JEONBUK");
    assertThat(service.searchPlaces(1, "전북특별자치도"))
        .extracting(PlaceCandidateDto::regionCode)
        .containsExactly("JEONBUK");
    // 한 글자는 앞부분으로 맞추지 않는다 (경 → 경기·경북·경남이 되지 않게)
    assertThat(service.searchPlaces(1, "경")).isEmpty();
  }

  @Test
  void 시도와_맞으면_한도를_넘거나_공급자가_실패해도_그_지역은_준다() {
    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 0, true, 10, Duration.ofSeconds(2));
    regionsAndFixtures(Map.of());

    assertThat(service.searchPlaces(1, "서울"))
        .extracting(PlaceCandidateDto::regionCode)
        .containsExactly("SEOUL");
    assertThat(error(() -> service.searchPlaces(1, "도쿄")).code())
        .isEqualTo(ErrorCode.WEATHER_LIMIT);
    assertThat(fakes.geocoded).isEmpty();

    fakes.options =
        new PlaceWeatherOptions(
            Duration.ofHours(3), 30, Duration.ofDays(1), 100, true, 10, Duration.ofSeconds(2));
    fakes.geoAnswer = q -> PlaceWeatherFakes.geoFailed();
    base.searchAnswers.put(
        "WEATHERAPI", (p, q) -> SearchResult.failed(FetchErrors.HTTP_5XX, "x", List.of()));
    service = fakes.service();
    assertThat(service.searchPlaces(1, "부산"))
        .extracting(PlaceCandidateDto::regionCode)
        .containsExactly("BUSAN");
    assertThat(fakes.searchRows).isEmpty();
  }

  @Test
  void REGION_붙이기는_30km_안의_가장_가까운_활성_지역이다() {
    assertThat(PlaceWeatherService.nearRegion(base.regions, 37.57, 126.98))
        .map(WeatherRegion::code)
        .hasValue("SEOUL");
    // 서울시청에서 약 40km (수원 근처, 테스트 지역에는 경기가 없다)
    assertThat(PlaceWeatherService.nearRegion(base.regions, 37.26, 127.03)).isEmpty();
    assertThat(PlaceWeatherService.normalize("  A  b\tC ")).isEqualTo("a b c");
  }
}
