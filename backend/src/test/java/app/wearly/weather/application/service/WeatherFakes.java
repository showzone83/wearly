package app.wearly.weather.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroupEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.weather.application.port.out.RefreshExecutorPort;
import app.wearly.weather.application.port.out.RefreshLockPort;
import app.wearly.weather.application.port.out.WeatherLogQueryPort;
import app.wearly.weather.application.port.out.WeatherProviderPort;
import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.application.port.out.WeatherSettingPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.application.port.out.WeatherSourcePort;
import app.wearly.weather.application.port.out.dto.CallRowDto;
import app.wearly.weather.application.port.out.dto.CallSummaryRowDto;
import app.wearly.weather.application.port.out.dto.ProviderUsageRowDto;
import app.wearly.weather.application.port.out.dto.RunRowDto;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.CurrentWeather;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.KeyState;
import app.wearly.weather.domain.ProviderHealth;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherOptions;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherReport;
import app.wearly.weather.domain.WeatherSetting;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** 날씨 서비스 단위 테스트용 가짜 포트 (Spring·DB·HTTP 없이) */
final class WeatherFakes {

  static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  WeatherSetting setting = new WeatherSetting(true, 30, 3, 3, 15, null, null);
  final Map<String, WeatherProvider> providerRows = new LinkedHashMap<>();
  final Map<String, WeatherSnapshot> snapshotRows = new HashMap<>();
  final List<RefreshRun> runRows = new ArrayList<>();
  final List<CallRecord> callRows = new ArrayList<>();
  final List<String> fetched = new ArrayList<>();
  final Map<String, KeyState> keys = new HashMap<>();
  final Map<String, BiFunction<WeatherProvider, WeatherRegion, FetchResult>> answers =
      new HashMap<>();
  boolean lockHeld;
  int releases;

  final List<WeatherRegion> regions =
      List.of(
          new WeatherRegion("SEOUL", "서울", 37.5665, 126.978, true),
          new WeatherRegion("BUSAN", "부산", 35.1796, 129.0756, true),
          new WeatherRegion("JEJU", "제주", 33.4996, 126.5312, true));

  void provider(String code, int priority, Integer quota) {
    providerRows.put(
        code,
        new WeatherProvider(
            code,
            code + " 공급자",
            true,
            priority,
            code + "_KEY",
            quota,
            3,
            ProviderHealth.fresh(),
            null,
            null));
    keys.put(code, KeyState.CONFIGURED);
    answers.put(code, (p, r) -> ok(p, r));
  }

  FetchResult ok(WeatherProvider p, WeatherRegion r) {
    CurrentWeather cur =
        new CurrentWeather(18.5, 17.0, WeatherCondition.SUNNY, "맑음", NOW.minusSeconds(60));
    DailyForecast d =
        new DailyForecast(
            LocalDate.parse("2026-10-17"), 16, 10, 21, 10, WeatherCondition.SUNNY, "맑음");
    return FetchResult.success(
        new WeatherReport(r.label(), cur, List.of(d), Map.of("from", p.code())),
        List.of(call(p, r, true, 200, null, null)));
  }

  FetchResult fail(WeatherProvider p, WeatherRegion r, String code, Integer status) {
    String message = p.code() + " " + code;
    return FetchResult.failed(code, message, List.of(call(p, r, false, status, code, message)));
  }

  static CallRecord call(
      WeatherProvider p,
      WeatherRegion r,
      boolean success,
      Integer status,
      String code,
      String message) {
    return new CallRecord(
        r.code(),
        p.code(),
        CallRecord.FORECAST,
        false,
        status,
        success,
        code,
        message,
        120,
        900,
        NOW);
  }

  static BiFunction<WeatherProvider, WeatherRegion, FetchResult> failing(
      WeatherFakes f, String code, Integer status) {
    return (p, r) -> f.fail(p, r, code, status);
  }

  long callsTo(String provider) {
    return callRows.stream().filter(c -> c.providerCode().equals(provider)).count();
  }

  // ---------------------------------------------------------------- 포트

  final WeatherSettingPort settings =
      new WeatherSettingPort() {
        @Override
        public Optional<WeatherSetting> load() {
          return Optional.ofNullable(setting);
        }

        @Override
        public WeatherSetting save(WeatherSetting s) {
          setting = s;
          return s;
        }
      };

  final WeatherProviderPort providers =
      new WeatherProviderPort() {
        @Override
        public List<WeatherProvider> findAll() {
          return providerRows.values().stream()
              .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
              .toList();
        }

        @Override
        public Optional<WeatherProvider> find(String code) {
          return Optional.ofNullable(providerRows.get(code));
        }

        @Override
        public WeatherProvider saveConfig(WeatherProvider p) {
          providerRows.put(p.code(), p);
          return p;
        }

        @Override
        public void saveHealth(String code, ProviderHealth health) {
          providerRows.put(code, providerRows.get(code).withHealth(health));
        }
      };

  final WeatherSnapshotPort snapshots =
      new WeatherSnapshotPort() {
        @Override
        public Optional<WeatherSnapshot> find(String regionCode) {
          return Optional.ofNullable(snapshotRows.get(regionCode));
        }

        @Override
        public List<WeatherSnapshot> findAll() {
          return List.copyOf(snapshotRows.values());
        }

        @Override
        public void save(WeatherSnapshot s) {
          snapshotRows.put(s.regionCode(), s);
        }

        @Override
        public void recordError(String regionCode, String error, Instant at) {
          WeatherSnapshot s = snapshotRows.get(regionCode);
          snapshotRows.put(
              regionCode,
              s == null
                  ? new WeatherSnapshot(regionCode, null, null, null, null, null, null, error, at)
                  : new WeatherSnapshot(
                      regionCode,
                      s.providerCode(),
                      s.current(),
                      s.days(),
                      s.raw(),
                      s.locationName(),
                      s.fetchedAt(),
                      error,
                      at));
        }

        @Override
        public Optional<Instant> latestFetchedAt() {
          return snapshotRows.values().stream()
              .map(WeatherSnapshot::fetchedAt)
              .filter(java.util.Objects::nonNull)
              .max(Instant::compareTo);
        }
      };

  final WeatherRunPort runs =
      new WeatherRunPort() {
        @Override
        public RefreshRun create(RefreshRun run) {
          RefreshRun saved = run.withId(runRows.size() + 1L);
          runRows.add(saved);
          return saved;
        }

        @Override
        public void update(RefreshRun run) {
          runRows.set((int) (run.runId() - 1), run);
        }

        @Override
        public void saveCalls(long runId, List<CallRecord> calls) {
          callRows.addAll(calls);
        }

        @Override
        public Optional<RefreshRun> find(long runId) {
          return Optional.ofNullable(runRows.get((int) (runId - 1)));
        }

        @Override
        public Optional<RefreshRun> latestFinished() {
          return runRows.stream().filter(r -> r.finishedAt() != null).reduce((a, b) -> b);
        }

        @Override
        public int closeAbandoned(Instant startedBefore, Instant now) {
          return 0;
        }

        @Override
        public int purgeBefore(Instant startedBefore) {
          return 0;
        }
      };

  /** 월 사용량만 (가짜 호출 기록에서 센다). 목록 조회는 쓰지 않는다 */
  final WeatherLogQueryPort logs =
      new WeatherLogQueryPort() {
        @Override
        public List<RunRowDto> findRuns(RunFilter filter, int limit, long offset) {
          throw new UnsupportedOperationException();
        }

        @Override
        public long countRuns(RunFilter filter) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<CallRowDto> findCalls(CallFilter filter, int limit, long offset) {
          throw new UnsupportedOperationException();
        }

        @Override
        public long countCalls(CallFilter filter) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<ProviderUsageRowDto> usage(Instant from, Instant to) {
          Map<String, Long> m = new LinkedHashMap<>();
          callRows.forEach(c -> m.merge(c.providerCode(), 1L, Long::sum));
          return m.entrySet().stream()
              .map(e -> new ProviderUsageRowDto(e.getKey(), e.getValue()))
              .toList();
        }

        @Override
        public CallSummaryRowDto summary(Instant from, Instant to) {
          throw new UnsupportedOperationException();
        }
      };

  final WeatherSourcePort sources =
      new WeatherSourcePort() {
        @Override
        public KeyState keyState(WeatherProvider provider) {
          return keys.getOrDefault(provider.code(), KeyState.MISSING);
        }

        @Override
        public int callsPerFetch(String providerCode) {
          return 1;
        }

        @Override
        public FetchResult fetch(WeatherProvider provider, WeatherRegion region, int days) {
          fetched.add(provider.code() + ":" + region.code());
          return answers.get(provider.code()).apply(provider, region);
        }
      };

  final RefreshLockPort lock =
      new RefreshLockPort() {
        @Override
        public boolean tryAcquire(String owner, java.time.Duration ttl) {
          if (lockHeld) {
            return false;
          }
          lockHeld = true;
          return true;
        }

        @Override
        public void release(String owner) {
          lockHeld = false;
          releases++;
        }

        @Override
        public boolean held() {
          return lockHeld;
        }
      };

  /** 바로 실행한다 (테스트에서 비동기 대신) */
  final RefreshExecutorPort executor = Runnable::run;

  final CodeCatalog catalog =
      () ->
          new CodeSnapshot(
              "v1",
              List.of(
                  new CodeGroupEntry(
                      CodeGroups.REGION,
                      "날씨 지역",
                      null,
                      90,
                      regions.stream()
                          .map(
                              r ->
                                  new CodeEntry(
                                      r.code(),
                                      r.label(),
                                      null,
                                      0,
                                      r.active(),
                                      Map.<String, Object>of("lat", r.lat(), "lon", r.lon())))
                          .toList())));

  WeatherRefreshRunner runner() {
    return new WeatherRefreshRunner(
        settings, providers, snapshots, runs, logs, sources, catalog, clock);
  }

  WeatherScheduleService schedule() {
    return new WeatherScheduleService(
        runner(), runs, snapshots, lock, new WeatherOptions(false, true, 90), clock);
  }

  static final String AUTH = FetchErrors.AUTH;
}
