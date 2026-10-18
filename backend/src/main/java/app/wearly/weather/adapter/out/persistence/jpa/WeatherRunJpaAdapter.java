package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.WeatherRunPort;
import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.RefreshRun;
import app.wearly.weather.domain.RunStatus;
import app.wearly.weather.domain.RunTrigger;
import app.wearly.weather.domain.SkipReason;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** WeatherRunPort 구현 (JPA). 실행은 트랜잭션 밖에서 돌므로 쓰기마다 짧은 트랜잭션을 연다 */
@Component
class WeatherRunJpaAdapter implements WeatherRunPort {

  static final int MAX_ERROR = 300;

  private final WeatherRefreshRunJpaRepository runs;
  private final WeatherCallLogJpaRepository calls;

  WeatherRunJpaAdapter(WeatherRefreshRunJpaRepository runs, WeatherCallLogJpaRepository calls) {
    this.runs = runs;
    this.calls = calls;
  }

  @Override
  @Transactional
  public RefreshRun create(RefreshRun run) {
    WeatherRefreshRunEntity e =
        new WeatherRefreshRunEntity(
            run.trigger().name(), run.requestedBy(), run.regionCode(), run.startedAt());
    apply(e, run);
    return toDomain(runs.saveAndFlush(e));
  }

  @Override
  @Transactional
  public void update(RefreshRun run) {
    WeatherRefreshRunEntity e =
        runs.findById(run.runId())
            .orElseThrow(() -> new NoSuchElementException("run " + run.runId()));
    apply(e, run);
    runs.saveAndFlush(e);
  }

  @Override
  @Transactional
  public void saveCalls(long runId, List<CallRecord> records) {
    calls.saveAllAndFlush(
        records.stream()
            .map(
                c ->
                    new WeatherCallLogEntity(
                        runId,
                        c.regionCode(),
                        c.providerCode(),
                        c.callKind(),
                        c.fallback(),
                        c.httpStatus(),
                        c.success(),
                        c.errorCode(),
                        truncate(c.errorMessage()),
                        (int) Math.min(Integer.MAX_VALUE, c.durationMs()),
                        c.responseBytes(),
                        c.calledAt()))
            .toList());
  }

  @Override
  public Optional<RefreshRun> find(long runId) {
    return runs.findById(runId).map(WeatherRunJpaAdapter::toDomain);
  }

  @Override
  public Optional<RefreshRun> latestFinished() {
    return runs.findFirstByTriggerNotAndFinishedAtIsNotNullOrderByFinishedAtDescRunIdDesc(
            RunTrigger.TEST.name())
        .map(WeatherRunJpaAdapter::toDomain);
  }

  @Override
  @Transactional
  public int closeAbandoned(Instant startedBefore, Instant now) {
    return runs.closeAbandoned(startedBefore, now);
  }

  @Override
  @Transactional
  public int purgeBefore(Instant startedBefore) {
    return runs.deleteStartedBefore(startedBefore);
  }

  private static void apply(WeatherRefreshRunEntity e, RefreshRun run) {
    e.result(
        run.status().name(),
        run.skipReason() == null ? null : run.skipReason().name(),
        run.successCount(),
        run.failCount(),
        run.fallbackCount(),
        run.finishedAt());
  }

  private static RefreshRun toDomain(WeatherRefreshRunEntity e) {
    return new RefreshRun(
        e.getRunId(),
        RunTrigger.valueOf(e.getTrigger()),
        e.getRequestedBy(),
        e.getRegionCode(),
        RunStatus.valueOf(e.getStatus()),
        e.getSkipReason() == null ? null : SkipReason.valueOf(e.getSkipReason()),
        e.getSuccessCount(),
        e.getFailCount(),
        e.getFallbackCount(),
        e.getStartedAt(),
        e.getFinishedAt());
  }

  private static String truncate(String s) {
    return s == null || s.length() <= MAX_ERROR ? s : s.substring(0, MAX_ERROR - 1) + "…";
  }
}
