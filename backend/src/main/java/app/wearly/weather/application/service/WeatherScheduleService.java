package app.wearly.weather.application.service;

import app.wearly.weather.application.port.in.WeatherScheduleUseCase;
import app.wearly.weather.application.port.out.RefreshLockPort;
import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunTrigger;
import app.wearly.weather.domain.SkipReason;
import app.wearly.weather.domain.WeatherOptions;
import app.wearly.weather.domain.WeatherSetting;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 서버 주기 실행 (17 문서 3장). 스케줄러 스레드에서 돌고, 잠금으로 수동 실행·다른 서버와 겹치지 않게 한다 */
@Service
public class WeatherScheduleService implements WeatherScheduleUseCase {

  private static final Logger log = LoggerFactory.getLogger(WeatherScheduleService.class);

  private final WeatherRefreshRunner runner;
  private final WeatherRunPort runs;
  private final WeatherSnapshotPort snapshots;
  private final RefreshLockPort lock;
  private final WeatherOptions options;
  private final Clock clock;

  public WeatherScheduleService(
      WeatherRefreshRunner runner,
      WeatherRunPort runs,
      WeatherSnapshotPort snapshots,
      RefreshLockPort lock,
      WeatherOptions options,
      Clock clock) {
    this.runner = runner;
    this.runs = runs;
    this.snapshots = snapshots;
    this.lock = lock;
    this.options = options;
    this.clock = clock;
  }

  @Override
  public Instant nextRunAt(Instant lastFinished) {
    if (lastFinished == null) {
      return clock.instant().plus(WeatherOptions.STARTUP_DELAY);
    }
    return runner.setting().nextRunAfter(lastFinished);
  }

  @Override
  public void runScheduled(boolean startup) {
    Instant now = clock.instant();
    WeatherSetting setting = runner.setting();
    RunTrigger trigger = startup ? RunTrigger.STARTUP : RunTrigger.SCHEDULE;
    if (startup) {
      // LOCK_TTL보다 최근에 시작한 RUNNING은 다른 서버가 아직 돌리는 중일 수 있어 두고, 그보다 오래된 것만 닫는다
      int closed = runs.closeAbandoned(now.minus(WeatherOptions.LOCK_TTL), now);
      if (closed > 0) {
        log.warn("서버가 멈춰 끝나지 않은 날씨 실행 {}건을 FAILED로 닫았어요", closed);
      }
      Optional<Instant> latest = snapshots.latestFetchedAt();
      if (latest.isPresent() && latest.get().plus(setting.interval()).isAfter(now)) {
        log.info("저장된 날씨가 최신이라 시작 실행을 건너뛰어요 (마지막 {})", latest.get());
        return;
      }
    }
    if (!setting.enabled()) {
      runs.create(RefreshRun.skipped(trigger, SkipReason.DISABLED, now));
      return;
    }
    // 잠금 전 검사라 그 사이 공급자 상태가 바뀔 수 있지만, 실행이 지역마다 다시 확인하므로 괜찮다
    if (!runner.anyUsable()) {
      log.warn("부를 수 있는 날씨 공급자가 없어 건너뛰어요 (사용 여부·키·차단·월 한도 확인)");
      runs.create(RefreshRun.skipped(trigger, SkipReason.NO_PROVIDER, now));
      return;
    }
    String owner = UUID.randomUUID().toString();
    if (!lock.tryAcquire(owner, WeatherOptions.LOCK_TTL)) {
      runs.create(RefreshRun.skipped(trigger, SkipReason.LOCKED, now));
      return;
    }
    try {
      RefreshRun run = runs.create(RefreshRun.start(trigger, null, null, now));
      runner.executeSafely(run, runner.activeRegions());
    } finally {
      lock.release(owner);
    }
  }

  @Override
  public void purgeOldLogs() {
    Instant cutoff = clock.instant().minus(Duration.ofDays(options.logRetentionDays()));
    int deleted = runs.purgeBefore(cutoff);
    if (deleted > 0) {
      log.info("{}일 지난 날씨 실행 이력 {}건을 지웠어요", options.logRetentionDays(), deleted);
    }
  }
}
