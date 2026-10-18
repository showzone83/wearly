package app.wearly.weather.domain;

import java.time.Instant;

/**
 * 날씨 받기 실행 한 번 (tb_weather_refresh_run). 지역별 결과를 세다가 끝나면 상태를 정한다.
 *
 * @param runId 저장 전에는 null
 * @param regionCode 한 지역만 실행했으면 그 지역, 전체면 null
 * @param fallbackCount 1순위가 아닌 공급자로 받은 지역 수 (예비 공급자를 붙이면 쓴다)
 */
public record RefreshRun(
    Long runId,
    RunTrigger trigger,
    Long requestedBy,
    String regionCode,
    RunStatus status,
    SkipReason skipReason,
    int successCount,
    int failCount,
    int fallbackCount,
    Instant startedAt,
    Instant finishedAt) {

  public static RefreshRun start(
      RunTrigger trigger, Long requestedBy, String regionCode, Instant now) {
    return new RefreshRun(
        null, trigger, requestedBy, regionCode, RunStatus.RUNNING, null, 0, 0, 0, now, null);
  }

  /** 시작하지 않고 건너뛴 실행 (꺼짐·공급자 없음·다른 실행 중) */
  public static RefreshRun skipped(RunTrigger trigger, SkipReason reason, Instant now) {
    return new RefreshRun(null, trigger, null, null, RunStatus.SKIPPED, reason, 0, 0, 0, now, now);
  }

  public RefreshRun withId(long id) {
    return new RefreshRun(
        id,
        trigger,
        requestedBy,
        regionCode,
        status,
        skipReason,
        successCount,
        failCount,
        fallbackCount,
        startedAt,
        finishedAt);
  }

  /** 지역마다 성공·실패를 센 뒤 끝낸다. 모두 성공 SUCCESS, 모두 실패 FAILED, 섞이면 PARTIAL */
  public RefreshRun finish(int success, int fail, int fallback, Instant now) {
    RunStatus s =
        fail == 0 ? RunStatus.SUCCESS : success == 0 ? RunStatus.FAILED : RunStatus.PARTIAL;
    return new RefreshRun(
        runId, trigger, requestedBy, regionCode, s, null, success, fail, fallback, startedAt, now);
  }

  public boolean running() {
    return status == RunStatus.RUNNING;
  }
}
