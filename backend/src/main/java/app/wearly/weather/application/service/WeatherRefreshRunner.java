package app.wearly.weather.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.Zones;
import app.wearly.weather.application.port.out.WeatherLogQueryPort;
import app.wearly.weather.application.port.out.WeatherProviderPort;
import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.application.port.out.WeatherSettingPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.application.port.out.WeatherSourcePort;
import app.wearly.weather.application.port.out.dto.ProviderUsageRowDto;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.FetchErrors;
import app.wearly.weather.domain.FetchResult;
import app.wearly.weather.domain.ProviderHealth;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.WeatherProvider;
import app.wearly.weather.domain.WeatherRegion;
import app.wearly.weather.domain.WeatherSetting;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 날씨 받기 실행의 본체 (17 문서 4장). 잠금은 부르는 쪽이 잡는다. 외부 HTTP를 부르므로 트랜잭션 없이 돌고, 호출 기록·공급자 상태·저장값은 그때그때 따로 저장한다
 * (중간에 멈춰도 그때까지 받은 값은 남는다).
 *
 * <p>지역마다 부를 수 있는 공급자를 priority 순으로 시도하고, 실패하면 같은 지역을 다음 공급자로 시도한다. 지금은 공급자가 WEATHERAPI 하나라 실패하면 그
 * 지역은 이전 값을 그대로 둔다. 예비 공급자는 행과 어댑터만 더하면 이 순서에 들어온다.
 */
@Service
public class WeatherRefreshRunner {

  private static final Logger log = LoggerFactory.getLogger(WeatherRefreshRunner.class);

  private final WeatherSettingPort settings;
  private final WeatherProviderPort providers;
  private final WeatherSnapshotPort snapshots;
  private final WeatherRunPort runs;
  private final WeatherLogQueryPort logs;
  private final WeatherSourcePort sources;
  private final CodeCatalog catalog;
  private final Clock clock;

  public WeatherRefreshRunner(
      WeatherSettingPort settings,
      WeatherProviderPort providers,
      WeatherSnapshotPort snapshots,
      WeatherRunPort runs,
      WeatherLogQueryPort logs,
      WeatherSourcePort sources,
      CodeCatalog catalog,
      Clock clock) {
    this.settings = settings;
    this.providers = providers;
    this.snapshots = snapshots;
    this.runs = runs;
    this.logs = logs;
    this.sources = sources;
    this.catalog = catalog;
    this.clock = clock;
  }

  WeatherSetting setting() {
    return settings.load().orElseGet(WeatherSetting::defaults);
  }

  List<WeatherRegion> activeRegions() {
    return WeatherRegion.active(catalog.current());
  }

  /** 지금 부를 수 있는 공급자가 하나라도 있는지 */
  boolean anyUsable() {
    Instant now = clock.instant();
    Map<String, Long> used = monthUsage();
    return providers.findAll().stream().anyMatch(p -> usable(p, now, used));
  }

  /** 이번 달(한국 시간) 공급자별 호출 수 (실패 포함). 호출 이력을 세므로 이력 보관 일수가 한 달(31일)보다 짧으면 이번 달 이력이 지워져 한도를 적게 센다. */
  Map<String, Long> monthUsage() {
    LocalDate first = Zones.today(clock).withDayOfMonth(1);
    Map<String, Long> out = new HashMap<>();
    for (ProviderUsageRowDto r :
        logs.usage(Zones.startOf(first), Zones.startOf(first.plusMonths(1)))) {
      out.put(r.providerCode(), r.calls());
    }
    return out;
  }

  boolean usable(WeatherProvider p, Instant now, Map<String, Long> used) {
    return p.usable(
        sources.keyState(p).callable(),
        now,
        used.getOrDefault(p.code(), 0L),
        sources.callsPerFetch(p.code()));
  }

  /** 실행하다 예상하지 못한 오류(DB 등)가 나도 실행을 FAILED로 닫는다 */
  RefreshRun executeSafely(RefreshRun run, List<WeatherRegion> regions) {
    try {
      return execute(run, regions);
    } catch (RuntimeException e) {
      log.error("날씨 실행 {} 실패", run.runId(), e);
      // 그때까지 성공한 지역 수는 세지 않는다 (받은 저장값은 남는다)
      return markFailed(run, regions.size());
    }
  }

  /**
   * 실행을 FAILED로 닫는다 (실패 수는 최소 1이라 지역이 0개여도 SUCCESS가 되지 않는다). 상태 저장마저 실패하면 RUNNING으로 남고, 다음 서버 시작 때
   * closeAbandoned가 닫는다.
   */
  RefreshRun markFailed(RefreshRun run, int regionCount) {
    RefreshRun failed = run.finish(0, Math.max(1, regionCount), 0, clock.instant());
    try {
      runs.update(failed);
    } catch (RuntimeException ignored) {
      log.warn("날씨 실행 {} 상태를 저장하지 못했어요", run.runId());
    }
    return failed;
  }

  RefreshRun execute(RefreshRun run, List<WeatherRegion> regions) {
    WeatherSetting setting = setting();
    // 공급자 상태는 실행 중에 바뀌므로 복사본을 고쳐 가며 쓴다 (앞 지역에서 차단되면 다음 지역부터 건너뛴다)
    List<WeatherProvider> list = new ArrayList<>(providers.findAll());
    // DB에서 한 번만 세고 호출할 때마다 더한다 (지역마다 한도를 다시 본다)
    Map<String, Long> used = monthUsage();
    // 1순위 = priority가 가장 작은 "켜진" 공급자. 그 밖의 공급자로 받으면 fallback으로 센다
    String primary =
        list.stream()
            .filter(WeatherProvider::enabled)
            .map(WeatherProvider::code)
            .findFirst()
            .orElse(null);
    int ok = 0;
    int fail = 0;
    int fallbacks = 0;
    for (WeatherRegion region : regions) {
      Optional<String> error = Optional.of("부를 수 있는 공급자가 없어요.");
      boolean done = false;
      for (int i = 0; i < list.size() && !done; i++) {
        WeatherProvider p = list.get(i);
        if (!usable(p, clock.instant(), used)) {
          continue;
        }
        boolean fallback = !p.code().equals(primary);
        FetchResult r = fetch(p, region, setting.daysFor(p));
        runs.saveCalls(run.runId(), r.calls().stream().map(c -> c.asFallback(fallback)).toList());
        used.merge(p.code(), (long) r.calls().size(), Long::sum);
        Instant now = clock.instant();
        ProviderHealth health =
            r.ok()
                ? p.health().succeeded(now)
                : p.health()
                    .failed(
                        now,
                        r.errorCode(),
                        r.errorMessage(),
                        setting.failureThreshold(),
                        setting.cooldown());
        providers.saveHealth(p.code(), health);
        list.set(i, p.withHealth(health));
        if (r.ok()) {
          snapshots.save(WeatherSnapshot.of(region.code(), p.code(), r.report(), now));
          done = true;
          ok++;
          if (fallback) {
            fallbacks++;
          }
        } else {
          error = Optional.of(p.displayName() + ": " + r.errorMessage());
        }
      }
      if (!done) {
        fail++;
        snapshots.recordError(region.code(), error.get(), clock.instant());
      }
    }
    RefreshRun finished = run.finish(ok, fail, fallbacks, clock.instant());
    runs.update(finished);
    log.info(
        "날씨 실행 {} ({}) 끝: 성공 {} · 실패 {} · 예비 {}", run.runId(), run.trigger(), ok, fail, fallbacks);
    return finished;
  }

  /** 어댑터가 예외를 던져도 실패 결과로 바꾼다 (실행 전체가 멈추지 않게) */
  FetchResult fetch(WeatherProvider p, WeatherRegion region, int days) {
    try {
      return sources.fetch(p, region, days);
    } catch (RuntimeException e) {
      log.warn("날씨 공급자 {} 호출 중 예외 ({})", p.code(), e.getClass().getSimpleName());
      Instant now = clock.instant();
      String message = "호출 중 오류: " + e.getClass().getSimpleName();
      return FetchResult.failed(
          FetchErrors.CLIENT,
          message,
          List.of(
              new CallRecord(
                  region.code(),
                  p.code(),
                  CallRecord.FORECAST,
                  false,
                  null,
                  false,
                  FetchErrors.CLIENT,
                  message,
                  0,
                  null,
                  now)));
    }
  }
}
