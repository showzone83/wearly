package app.wearly.weather.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.Zones;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import app.wearly.weather.application.port.in.PlaceWeatherUseCase;
import app.wearly.weather.application.port.in.dto.ClimateInfoDto;
import app.wearly.weather.application.port.in.dto.DayWeatherDto;
import app.wearly.weather.application.port.in.dto.PlaceCandidateDto;
import app.wearly.weather.application.port.in.dto.PlaceDto;
import app.wearly.weather.application.port.in.dto.WeatherDayDto;
import app.wearly.weather.application.port.in.dto.WeatherDayQueryDto;
import app.wearly.weather.application.port.out.PlaceSearchCachePort;
import app.wearly.weather.application.port.out.PlaceSnapshotPort;
import app.wearly.weather.application.port.out.UsageLimitPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.application.port.out.WeatherSourcePort;
import app.wearly.weather.application.service.WeatherRefreshRunner.Attempt;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.MonthlyClimate;
import app.wearly.weather.domain.PlaceCandidate;
import app.wearly.weather.domain.PlaceSnapshot;
import app.wearly.weather.domain.PlaceWeatherOptions;
import app.wearly.weather.domain.SearchResult;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherReport;
import app.wearly.weather.domain.WeatherSetting;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * 장소별 날씨 (19 문서). 17 문서의 원칙("사용자 요청마다 외부 API를 부르지 않는다")은 REGION에 그대로 두고, REGION 밖 좌표만 요청 시점 호출 + 격자
 * 캐시 + 회원별 하루 한도로 절충한다.
 *
 * <ul>
 *   <li>REGION: 주기 수집한 스냅샷(tb_weather_snapshot)만 읽는다.
 *   <li>좌표: 격자 캐시(tb_weather_place_snapshot)가 place-cache-ttl보다 새로우면 그대로, 아니면 공급자를 한 번 부른다 (17 문서의
 *       순서·차단·월 한도, 격자 잠금). 한도를 넘으면 예전 캐시, 그다음 평년값, 그것도 없으면 429.
 *   <li>예보에 그 날짜가 없으면 그 격자·그 달의 평년값(CLIMATE).
 * </ul>
 *
 * <p>외부 HTTP를 부를 수 있어서 트랜잭션 없이 돈다 (읽기·쓰기는 포트마다 짧은 트랜잭션).
 */
@Service
public class PlaceWeatherService implements PlaceWeatherUseCase {

  static final String SEARCH_UNAVAILABLE = "장소 검색을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요.";
  static final String SEARCH_LIMIT = "오늘 장소 검색 횟수를 모두 썼어요. 내일 다시 시도해 주세요.";

  private final CodeCatalog catalog;
  private final WeatherSnapshotPort snapshots;
  private final PlaceSnapshotPort places;
  private final WeatherRefreshRunner runner;
  private final WeatherSourcePort sources;
  private final ClimateService climates;
  private final WeatherLocks locks;
  private final UsageLimitPort limits;
  private final PlaceSearchCachePort searchCache;
  private final PlaceWeatherOptions options;
  private final Clock clock;

  public PlaceWeatherService(
      CodeCatalog catalog,
      WeatherSnapshotPort snapshots,
      PlaceSnapshotPort places,
      WeatherRefreshRunner runner,
      WeatherSourcePort sources,
      ClimateService climates,
      WeatherLocks locks,
      UsageLimitPort limits,
      PlaceSearchCachePort searchCache,
      PlaceWeatherOptions options,
      Clock clock) {
    this.catalog = catalog;
    this.snapshots = snapshots;
    this.places = places;
    this.runner = runner;
    this.sources = sources;
    this.climates = climates;
    this.locks = locks;
    this.limits = limits;
    this.searchCache = searchCache;
    this.options = options;
    this.clock = clock;
  }

  // ---------------------------------------------------------------- 날짜 하나의 날씨

  @Override
  public WeatherDayDto day(WeatherDayQueryDto q) {
    LocalDate today = Zones.today(clock);
    List<FieldError> errors = new ArrayList<>();
    LocalDate date = parseDate(q.date(), today, errors);
    if (q.byCoordinates()) {
      // 좌표 방식은 로그인이 먼저다 (앱은 토큰 없는 region 요청의 401을 "예전 서버"로 본다. 좌표만 401)
      if (q.userId() == null) {
        throw new ApiException(ErrorCode.UNAUTHORIZED);
      }
      if (q.regionCode() != null && !q.regionCode().isBlank()) {
        errors.add(new FieldError("region", "지역과 좌표는 함께 보낼 수 없어요."));
      }
      if (q.lat() == null) {
        errors.add(new FieldError("lat", "위도가 필요해요."));
      } else if (!GridKey.validLat(q.lat())) {
        errors.add(new FieldError("lat", "위도는 -90~90이어야 해요."));
      }
      if (q.lon() == null) {
        errors.add(new FieldError("lon", "경도가 필요해요."));
      } else if (!GridKey.validLon(q.lon())) {
        errors.add(new FieldError("lon", "경도는 -180~180이어야 해요."));
      }
      ApiException.throwIfAny(errors);
      return coordinateDay(q.userId(), q.lat(), q.lon(), date, today);
    }
    String code =
        q.regionCode() == null || q.regionCode().isBlank()
            ? WeatherRegion.DEFAULT_CODE
            : q.regionCode().strip();
    Optional<WeatherRegion> region = WeatherRegion.find(catalog.current(), code);
    if (region.isEmpty()) {
      errors.add(new FieldError("region", "없는 지역이에요."));
    }
    ApiException.throwIfAny(errors);
    return regionDay(region.get(), date, today);
  }

  /** YYYY-MM-DD, 오늘(한국 날짜)부터 1년 안 */
  static LocalDate parseDate(String raw, LocalDate today, List<FieldError> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(new FieldError("date", "날짜가 필요해요."));
      return null;
    }
    LocalDate date;
    try {
      date = LocalDate.parse(raw.strip());
    } catch (DateTimeParseException e) {
      errors.add(new FieldError("date", "날짜는 YYYY-MM-DD 형식이어야 해요."));
      return null;
    }
    if (date.isBefore(today)) {
      errors.add(new FieldError("date", "오늘이나 그 뒤의 날짜여야 해요."));
    } else if (date.isAfter(today.plusYears(PlaceWeatherOptions.MAX_DAYS_AHEAD_YEARS))) {
      errors.add(new FieldError("date", "오늘부터 1년 안의 날짜여야 해요."));
    }
    return date;
  }

  /** REGION: 주기 수집한 스냅샷만 읽는다 (외부 호출은 평년값을 처음 계산할 때뿐) */
  private WeatherDayDto regionDay(WeatherRegion region, LocalDate date, LocalDate today) {
    Optional<WeatherSnapshot> snapshot =
        snapshots.find(region.code()).filter(WeatherSnapshot::hasData);
    List<DailyForecast> days = snapshot.map(s -> s.daysFrom(today)).orElse(List.of());
    PlaceDto place = new PlaceDto(region.code(), region.label(), region.lat(), region.lon());
    return answer(
        place,
        GridKey.of(region.lat(), region.lon()),
        date,
        days,
        snapshot.map(WeatherSnapshot::fetchedAt).orElse(null),
        false);
  }

  /** 좌표: 격자 캐시, 오래됐으면 공급자를 한 번 (회원별 하루 한도) */
  private WeatherDayDto coordinateDay(
      long userId, double lat, double lon, LocalDate date, LocalDate today) {
    GridKey grid = GridKey.of(lat, lon);
    PlaceSnapshot cache = places.find(grid.key()).filter(PlaceSnapshot::hasData).orElse(null);
    boolean limited = false;
    if (cache == null || !cache.fresh(clock.instant(), options.placeCacheTtl())) {
      Refresh r = refresh(userId, grid, cache, today);
      cache = r.snapshot();
      limited = r.limited();
    }
    List<DailyForecast> days = cache == null ? List.of() : cache.daysFrom(today);
    String label = cache != null && cache.label() != null ? cache.label() : grid.key();
    PlaceDto place = new PlaceDto(null, label, grid.lat(), grid.lon());
    return answer(place, grid, date, days, cache == null ? null : cache.fetchedAt(), limited);
  }

  /** 예보에 그 날짜가 있으면 FORECAST, 없으면 평년값, 둘 다 없으면 429(한도 때문) 또는 503 */
  private WeatherDayDto answer(
      PlaceDto place,
      GridKey grid,
      LocalDate date,
      List<DailyForecast> days,
      Instant fetchedAt,
      boolean limited) {
    LocalDate until = days.isEmpty() ? null : days.getLast().date();
    Optional<DailyForecast> forecast = days.stream().filter(d -> d.date().equals(date)).findFirst();
    if (forecast.isPresent()) {
      DailyForecast d = forecast.get();
      return new WeatherDayDto(
          place,
          date,
          WeatherDayDto.FORECAST,
          new DayWeatherDto(
              d.temp(), d.min(), d.max(), d.rainProb(), d.cond().name(), d.condText()),
          until,
          fetchedAt,
          null);
    }
    Optional<MonthlyClimate> climate = climates.monthly(grid, date.getMonthValue());
    if (climate.isPresent()) {
      MonthlyClimate c = climate.get();
      return new WeatherDayDto(
          place,
          date,
          WeatherDayDto.CLIMATE,
          new DayWeatherDto(c.tempAvg(), c.tempMin(), c.tempMax(), c.rainProb(), null, null),
          until,
          c.computedAt(),
          new ClimateInfoDto(c.years(), c.rainDayRatio()));
    }
    throw new ApiException(limited ? ErrorCode.WEATHER_LIMIT : ErrorCode.WEATHER_UNAVAILABLE);
  }

  /**
   * 새로 받은 결과.
   *
   * @param snapshot 지금 쓸 수 있는 가장 좋은 캐시 (새로 받았거나 예전 값, 없으면 null)
   * @param limited 회원 하루 한도를 넘어 공급자를 부르지 못했는지
   */
  record Refresh(PlaceSnapshot snapshot, boolean limited) {}

  /**
   * 격자 예보를 새로 받는다. 부를 공급자가 없으면(차단·한도·키) 부르지 않는다. 같은 격자를 다른 요청이 받고 있으면 기다렸다가 그 결과를 쓴다. 회원 한도는 실제로
   * 부르기 직전에 센다.
   */
  private Refresh refresh(long userId, GridKey grid, PlaceSnapshot cache, LocalDate today) {
    if (!runner.anyUsable()) {
      return new Refresh(cache, false);
    }
    String name = "place:" + grid.key();
    String owner =
        locks.acquireOrWait(name, options.lockWait(), () -> freshCache(grid).isPresent());
    if (owner == null) {
      return new Refresh(
          places.find(grid.key()).filter(PlaceSnapshot::hasData).orElse(cache), false);
    }
    try {
      Optional<PlaceSnapshot> fresh = freshCache(grid);
      if (fresh.isPresent()) {
        return new Refresh(fresh.get(), false);
      }
      if (!limits.tryConsume(CallRecord.ON_DEMAND, userId, today, options.onDemandDailyLimit())) {
        return new Refresh(cache, true);
      }
      String label = cache != null && cache.label() != null ? cache.label() : grid.key();
      WeatherRegion target = new WeatherRegion(grid.key(), label, grid.lat(), grid.lon(), true);
      WeatherSetting setting = runner.setting();
      Attempt<FetchResult> a =
          runner.callOnDemand(
              CallRecord.ON_DEMAND,
              grid.key(),
              p -> true,
              p -> sources.fetch(p, target, setting.daysFor(p)));
      if (a.ok()) {
        WeatherReport report = a.result().report();
        String locationLabel = report.locationName() == null ? label : report.locationName();
        PlaceSnapshot saved =
            PlaceSnapshot.of(grid, locationLabel, a.providerCode(), report, clock.instant());
        places.save(saved);
        return new Refresh(saved, false);
      }
      places.recordError(grid, a.error(), clock.instant());
      return new Refresh(cache, false);
    } finally {
      locks.release(name, owner);
    }
  }

  private Optional<PlaceSnapshot> freshCache(GridKey grid) {
    return places.find(grid.key()).filter(s -> s.fresh(clock.instant(), options.placeCacheTtl()));
  }

  // ---------------------------------------------------------------- 장소 검색

  @Override
  public List<PlaceCandidateDto> searchPlaces(long userId, String query) {
    String q = normalize(query);
    if (q.isEmpty()) {
      throw ApiException.validation("q", "검색어를 입력해 주세요.");
    }
    if (q.length() > PlaceWeatherOptions.MAX_QUERY) {
      throw ApiException.validation("q", PlaceWeatherOptions.MAX_QUERY + "자까지 검색할 수 있어요.");
    }
    List<PlaceCandidate> items = searchCache.find(q).orElse(null);
    if (items == null) {
      Predicate<WeatherProvider> supports = p -> sources.supportsSearch(p.code());
      if (!runner.anyUsable(supports)) {
        throw new ApiException(ErrorCode.WEATHER_UNAVAILABLE, SEARCH_UNAVAILABLE);
      }
      if (!limits.tryConsume(
          CallRecord.SEARCH, userId, Zones.today(clock), options.searchDailyLimit())) {
        throw new ApiException(ErrorCode.WEATHER_LIMIT, SEARCH_LIMIT);
      }
      Attempt<SearchResult> a =
          runner.callOnDemand(CallRecord.SEARCH, null, supports, p -> sources.search(p, q));
      if (!a.ok()) {
        throw new ApiException(ErrorCode.WEATHER_UNAVAILABLE, SEARCH_UNAVAILABLE);
      }
      items = a.result().items().stream().limit(PlaceWeatherOptions.MAX_SEARCH_RESULTS).toList();
      searchCache.save(q, items, options.searchCacheTtl());
    }
    List<WeatherRegion> regions = WeatherRegion.active(catalog.current());
    return items.stream()
        .map(
            c ->
                new PlaceCandidateDto(
                    c.label(),
                    c.region(),
                    c.country(),
                    c.lat(),
                    c.lon(),
                    nearRegion(regions, c.lat(), c.lon()).map(WeatherRegion::code).orElse(null)))
        .toList();
  }

  /** 검색어 정리: 앞뒤 공백 제거, 공백 여러 개는 하나로, 소문자 (캐시 키) */
  static String normalize(String query) {
    return query == null ? "" : query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  /** 좌표와 30km(REGION_NEAR_KM) 안에 있는 가장 가까운 활성 REGION */
  static Optional<WeatherRegion> nearRegion(List<WeatherRegion> regions, double lat, double lon) {
    return regions.stream()
        .filter(
            r ->
                GridKey.distanceKm(lat, lon, r.lat(), r.lon())
                    <= PlaceWeatherOptions.REGION_NEAR_KM)
        .min(Comparator.comparingDouble(r -> GridKey.distanceKm(lat, lon, r.lat(), r.lon())));
  }
}
