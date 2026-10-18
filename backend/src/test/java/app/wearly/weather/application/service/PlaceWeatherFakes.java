package app.wearly.weather.application.service;

import app.wearly.weather.application.port.out.ClimatePort;
import app.wearly.weather.application.port.out.ClimateSourcePort;
import app.wearly.weather.application.port.out.GeocodingPort;
import app.wearly.weather.application.port.out.PlaceSearchCachePort;
import app.wearly.weather.application.port.out.PlaceSnapshotPort;
import app.wearly.weather.application.port.out.UsageLimitPort;
import app.wearly.weather.application.port.out.WeatherLockPort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.ClimateFetch;
import app.wearly.weather.domain.DailyObservation;
import app.wearly.weather.domain.GeoPlace;
import app.wearly.weather.domain.GeocodeResult;
import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.MonthlyClimate;
import app.wearly.weather.domain.PlaceCandidate;
import app.wearly.weather.domain.PlaceSnapshot;
import app.wearly.weather.domain.PlaceWeatherOptions;
import app.wearly.weather.domain.WeatherRegion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 장소별 날씨 서비스 단위 테스트용 가짜 포트 (WeatherFakes 위에 19 문서의 포트를 더한다) */
final class PlaceWeatherFakes {

  final WeatherFakes base = new WeatherFakes();

  PlaceWeatherOptions options =
      new PlaceWeatherOptions(
          Duration.ofHours(3), 30, Duration.ofDays(1), 100, true, 10, Duration.ofSeconds(2));

  final Map<String, PlaceSnapshot> placeRows = new ConcurrentHashMap<>();
  final Map<String, MonthlyClimate> climateRows = new ConcurrentHashMap<>();
  final Map<String, Integer> usage = new ConcurrentHashMap<>();
  final Map<String, List<PlaceCandidate>> searchRows = new ConcurrentHashMap<>();
  final Map<String, String> lockRows = new ConcurrentHashMap<>();
  final List<String> climateFetches = Collections.synchronizedList(new ArrayList<>());
  final AtomicInteger climateSaves = new AtomicInteger();

  /** 지오코딩 사용 여부. 기본은 꺼 둔다 (날씨 공급자 검색 테스트가 그대로 돌게) */
  boolean geoEnabled = false;

  /** 지오코딩에 넘긴 검색어 (차례대로) */
  final List<String> geocoded = Collections.synchronizedList(new ArrayList<>());

  /** 지오코딩 답 (검색어 → 결과). 기본은 결과 없음 */
  Function<String, GeocodeResult> geoAnswer = q -> geoOk(List.of());

  static GeocodeResult geoOk(List<GeoPlace> places) {
    return GeocodeResult.success(
        places,
        List.of(
            new CallRecord(
                null,
                GeocodingPort.PROVIDER_CODE,
                CallRecord.SEARCH,
                false,
                200,
                true,
                null,
                null,
                50,
                300,
                WeatherFakes.NOW)));
  }

  /** 실제 Open-Meteo 지오코딩 응답 고정본 (fixtures/weather/open-meteo-geocoding-&lt;name&gt;.json) → 장소 */
  static List<GeoPlace> geoFixture(String name) {
    String body;
    try (InputStream in =
        PlaceWeatherFakes.class.getResourceAsStream(
            "/fixtures/weather/open-meteo-geocoding-" + name + ".json")) {
      body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    List<GeoPlace> out = new ArrayList<>();
    for (JsonNode n : JsonMapper.builder().build().readTree(body).path("results")) {
      JsonNode pop = n.path("population");
      out.add(
          new GeoPlace(
              n.path("name").asString(null),
              n.path("admin1").asString(null),
              n.path("country").asString(null),
              n.path("country_code").asString(null),
              n.path("feature_code").asString(null),
              pop.isNumber() ? pop.asLong() : null,
              n.path("latitude").asDouble(),
              n.path("longitude").asDouble()));
    }
    return out;
  }

  /** 시·도 17개 (V11 마이그레이션의 REGION 코드와 같은 값) */
  static List<WeatherRegion> allRegions() {
    return List.of(
        new WeatherRegion("SEOUL", "서울", 37.5665, 126.978, true),
        new WeatherRegion("BUSAN", "부산", 35.1796, 129.0756, true),
        new WeatherRegion("DAEGU", "대구", 35.8714, 128.6014, true),
        new WeatherRegion("INCHEON", "인천", 37.4563, 126.7052, true),
        new WeatherRegion("GWANGJU", "광주", 35.1595, 126.8526, true),
        new WeatherRegion("DAEJEON", "대전", 36.3504, 127.3845, true),
        new WeatherRegion("ULSAN", "울산", 35.5384, 129.3114, true),
        new WeatherRegion("SEJONG", "세종", 36.48, 127.289, true),
        new WeatherRegion("GYEONGGI", "경기", 37.2636, 127.0286, true),
        new WeatherRegion("GANGWON", "강원", 37.8813, 127.7298, true),
        new WeatherRegion("CHUNGBUK", "충북", 36.6424, 127.489, true),
        new WeatherRegion("CHUNGNAM", "충남", 36.6588, 126.6728, true),
        new WeatherRegion("JEONBUK", "전북", 35.8242, 127.148, true),
        new WeatherRegion("JEONNAM", "전남", 34.8161, 126.4629, true),
        new WeatherRegion("GYEONGBUK", "경북", 36.576, 128.5056, true),
        new WeatherRegion("GYEONGNAM", "경남", 35.228, 128.6811, true),
        new WeatherRegion("JEJU", "제주", 33.4996, 126.5312, true));
  }

  static GeocodeResult geoFailed() {
    return GeocodeResult.failed(
        "HTTP_5XX",
        "Open-Meteo 지오코딩 API 오류 (HTTP 502)",
        List.of(
            new CallRecord(
                null,
                GeocodingPort.PROVIDER_CODE,
                CallRecord.SEARCH,
                false,
                502,
                false,
                "HTTP_5XX",
                "Open-Meteo 지오코딩 API 오류 (HTTP 502)",
                50,
                10,
                WeatherFakes.NOW)));
  }

  /** 평년값 자료 답. 기본은 1월과 10월만 있는 2년치 */
  Supplier<ClimateFetch> climateAnswer = () -> climateOk(sampleDays());

  ClimateFetch climateOk(List<DailyObservation> days) {
    return ClimateFetch.success(
        days,
        List.of(
            new CallRecord(
                "x",
                ClimateSourcePort.PROVIDER_CODE,
                CallRecord.CLIMATE,
                false,
                200,
                true,
                null,
                null,
                300,
                1000,
                WeatherFakes.NOW)));
  }

  ClimateFetch climateFailed() {
    return ClimateFetch.failed(
        "HTTP_5XX",
        "Open-Meteo API 오류 (HTTP 503)",
        List.of(
            new CallRecord(
                "x",
                ClimateSourcePort.PROVIDER_CODE,
                CallRecord.CLIMATE,
                false,
                503,
                false,
                "HTTP_5XX",
                "Open-Meteo API 오류 (HTTP 503)",
                300,
                10,
                WeatherFakes.NOW)));
  }

  /** 10월: 최고 20·최저 10, 넷 중 하루 비 / 1월: 최고 2·최저 -6, 비 없음 */
  static List<DailyObservation> sampleDays() {
    List<DailyObservation> out = new ArrayList<>();
    for (int year = 2024; year <= 2025; year++) {
      for (int d = 1; d <= 8; d++) {
        out.add(
            new DailyObservation(LocalDate.of(year, 10, d), 20.0, 10.0, d % 4 == 0 ? 3.0 : 0.0));
        out.add(new DailyObservation(LocalDate.of(year, 1, d), 2.0, -6.0, 0.0));
      }
    }
    return out;
  }

  // ---------------------------------------------------------------- 포트

  final PlaceSnapshotPort places =
      new PlaceSnapshotPort() {
        @Override
        public Optional<PlaceSnapshot> find(String gridKey) {
          return Optional.ofNullable(placeRows.get(gridKey));
        }

        @Override
        public void save(PlaceSnapshot s) {
          placeRows.put(s.gridKey(), s);
        }

        @Override
        public void recordError(GridKey grid, String error, Instant at) {
          PlaceSnapshot s = placeRows.get(grid.key());
          placeRows.put(
              grid.key(),
              s == null
                  ? new PlaceSnapshot(
                      grid.key(), grid.lat(), grid.lon(), null, null, null, null, null, error, at)
                  : new PlaceSnapshot(
                      s.gridKey(),
                      s.lat(),
                      s.lon(),
                      s.label(),
                      s.providerCode(),
                      s.current(),
                      s.days(),
                      s.fetchedAt(),
                      error,
                      at));
        }
      };

  final ClimatePort climates =
      new ClimatePort() {
        @Override
        public Optional<MonthlyClimate> find(String gridKey, int month) {
          return Optional.ofNullable(climateRows.get(gridKey + ":" + month));
        }

        @Override
        public void saveAll(List<MonthlyClimate> months) {
          climateSaves.incrementAndGet();
          months.forEach(m -> climateRows.put(m.gridKey() + ":" + m.month(), m));
        }
      };

  final ClimateSourcePort climateSource =
      new ClimateSourcePort() {
        @Override
        public String sourceCode() {
          return "OPEN_METEO";
        }

        @Override
        public ClimateFetch fetch(
            String target, double lat, double lon, LocalDate from, LocalDate to) {
          climateFetches.add(target + "@" + from + "~" + to);
          ClimateFetch f = climateAnswer.get();
          return new ClimateFetch(
              f.days(),
              f.calls().stream()
                  .map(
                      c ->
                          new CallRecord(
                              target,
                              c.providerCode(),
                              c.callKind(),
                              false,
                              c.httpStatus(),
                              c.success(),
                              c.errorCode(),
                              c.errorMessage(),
                              c.durationMs(),
                              c.responseBytes(),
                              c.calledAt()))
                  .toList(),
              f.errorCode(),
              f.errorMessage());
        }
      };

  final GeocodingPort geocoding =
      new GeocodingPort() {
        @Override
        public boolean enabled() {
          return geoEnabled;
        }

        @Override
        public GeocodeResult search(String query) {
          geocoded.add(query);
          return geoAnswer.apply(query);
        }
      };

  final WeatherLockPort locks =
      new WeatherLockPort() {
        @Override
        public boolean tryAcquire(String name, String owner, Duration ttl) {
          return lockRows.putIfAbsent(name, owner) == null;
        }

        @Override
        public void release(String name, String owner) {
          lockRows.remove(name, owner);
        }
      };

  final UsageLimitPort limits =
      (kind, userId, day, limit) -> {
        String key = kind + ":" + day + ":" + userId;
        synchronized (usage) {
          int used = usage.getOrDefault(key, 0);
          if (used >= limit) {
            return false;
          }
          usage.put(key, used + 1);
          return true;
        }
      };

  final PlaceSearchCachePort searchCache =
      new PlaceSearchCachePort() {
        @Override
        public Optional<List<PlaceCandidate>> find(String query) {
          return Optional.ofNullable(searchRows.get(query));
        }

        @Override
        public void save(String query, List<PlaceCandidate> items, Duration ttl) {
          searchRows.put(query, items);
        }
      };

  ClimateService climateService() {
    return new ClimateService(
        climates, climateSource, base.runs, new WeatherLocks(locks), options, base.clock);
  }

  PlaceWeatherService service() {
    return new PlaceWeatherService(
        base.catalog,
        base.snapshots,
        places,
        base.runner(),
        base.sources,
        climateService(),
        new WeatherLocks(locks),
        limits,
        searchCache,
        geocoding,
        base.runs,
        options,
        base.clock);
  }

  long onDemandCalls() {
    synchronized (base.callRows) {
      return base.callRows.stream().filter(c -> c.callKind().equals(CallRecord.ON_DEMAND)).count();
    }
  }
}
