package app.wearly.weather.application.service;

import app.wearly.common.domain.Zones;
import app.wearly.weather.application.port.out.ClimatePort;
import app.wearly.weather.application.port.out.ClimateSourcePort;
import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.ClimateFetch;
import app.wearly.weather.domain.ClimateYears;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.MonthlyClimate;
import app.wearly.weather.domain.PlaceWeatherOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 평년값 (19 문서 4장). 격자별 월 평년값을 DB에서 읽고, 없거나 400일이 넘었으면 과거 기상 자료(Open-Meteo)를 받아 12개월을 한 번에 계산해 둔다. 외부
 * HTTP를 부르므로 트랜잭션 없이 돌고, 같은 격자는 잠금으로 한 번만 계산한다.
 *
 * <p>설정 climate.enabled=false면 평년값을 주지 않는다 (CLIMATE 없음). 받기에 실패하면 예전 값이 있으면 그 값을 쓰고, 같은 격자는 10분 동안
 * 다시 부르지 않는다 (서버마다).
 */
@Service
public class ClimateService {

  private static final Logger log = LoggerFactory.getLogger(ClimateService.class);

  /** 받기에 실패한 격자를 다시 부르지 않는 시간 */
  static final Duration FAILURE_BACKOFF = Duration.ofMinutes(10);

  private final ClimatePort climates;
  private final ClimateSourcePort source;
  private final WeatherRunPort runs;
  private final WeatherLocks locks;
  private final PlaceWeatherOptions options;
  private final Clock clock;
  private final Map<String, Instant> failedUntil = new ConcurrentHashMap<>();

  public ClimateService(
      ClimatePort climates,
      ClimateSourcePort source,
      WeatherRunPort runs,
      WeatherLocks locks,
      PlaceWeatherOptions options,
      Clock clock) {
    this.climates = climates;
    this.source = source;
    this.runs = runs;
    this.locks = locks;
    this.options = options;
    this.clock = clock;
  }

  /** 그 격자·그 달의 평년값. 꺼져 있거나 구하지 못하면 비어 있다 */
  Optional<MonthlyClimate> monthly(GridKey grid, int month) {
    if (!options.climateEnabled()) {
      return Optional.empty();
    }
    Optional<MonthlyClimate> row = climates.find(grid.key(), month);
    if (row.filter(r -> r.fresh(clock.instant())).isPresent()) {
      return row;
    }
    Instant until = failedUntil.get(grid.key());
    if (until != null && until.isAfter(clock.instant())) {
      return row;
    }
    String name = "climate:" + grid.key();
    String owner = locks.acquireOrWait(name, options.lockWait(), () -> fresh(grid, month));
    if (owner == null) {
      return climates.find(grid.key(), month);
    }
    try {
      Optional<MonthlyClimate> again = climates.find(grid.key(), month);
      if (again.filter(r -> r.fresh(clock.instant())).isPresent()) {
        return again;
      }
      List<MonthlyClimate> months = compute(grid);
      if (months.isEmpty()) {
        failedUntil.put(grid.key(), clock.instant().plus(FAILURE_BACKOFF));
        return again;
      }
      failedUntil.remove(grid.key());
      return months.stream().filter(m -> m.month() == month).findFirst().or(() -> again);
    } finally {
      locks.release(name, owner);
    }
  }

  private boolean fresh(GridKey grid, int month) {
    return climates.find(grid.key(), month).filter(r -> r.fresh(clock.instant())).isPresent();
  }

  /** 지난 N개 완결 연도의 일별 자료를 받아 월별로 계산해 저장한다. 실패하면 빈 목록 */
  private List<MonthlyClimate> compute(GridKey grid) {
    ClimateYears years = ClimateYears.of(Zones.today(clock), options.climateYears());
    ClimateFetch f;
    try {
      f = source.fetch(grid.key(), grid.lat(), grid.lon(), years.from(), years.to());
    } catch (RuntimeException e) {
      String message = "호출 중 오류: " + e.getClass().getSimpleName();
      f =
          ClimateFetch.failed(
              FetchErrors.CLIENT,
              message,
              List.of(
                  new CallRecord(
                      grid.key(),
                      ClimateSourcePort.PROVIDER_CODE,
                      CallRecord.CLIMATE,
                      false,
                      null,
                      false,
                      FetchErrors.CLIENT,
                      message,
                      0,
                      null,
                      clock.instant())));
    }
    runs.saveCalls(null, f.calls().stream().map(c -> c.withKind(CallRecord.CLIMATE)).toList());
    if (!f.ok()) {
      log.warn("평년값 자료를 받지 못했어요 ({}): {}", grid.key(), f.errorMessage());
      return List.of();
    }
    List<MonthlyClimate> months =
        MonthlyClimate.compute(
            grid.key(), f.days(), years.label(), source.sourceCode(), clock.instant());
    if (months.isEmpty()) {
      log.warn("평년값 자료가 비어 있어요 ({})", grid.key());
      return List.of();
    }
    climates.saveAll(months);
    log.info("평년값 계산 {} ({}, {}개월)", grid.key(), years.label(), months.size());
    return months;
  }
}
