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
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 19 문서: 장소별 날씨 유스케이스 (가짜 포트). 순서 FORECAST → CLIMATE → 503, 좌표 캐시·TTL·격자 반올림, 회원 한도(429와 캐시 대체), 동시
 * 요청, 장소 검색(캐시·정리·REGION 붙이기·한도). 오늘은 한국 날짜 2026-10-17.
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
