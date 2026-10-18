package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.CallRecord;
import app.wearly.weather.domain.RefreshRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 실행 이력·호출 이력 쓰기 (tb_weather_refresh_run, tb_weather_call_log) */
public interface WeatherRunPort {

  /** 새 실행을 저장하고 runId가 든 실행을 돌려준다 */
  RefreshRun create(RefreshRun run);

  /** 상태·개수·끝난 시각을 바꾼다 */
  void update(RefreshRun run);

  /**
   * 호출 기록을 남긴다.
   *
   * @param runId 실행 밖 호출(ON_DEMAND·SEARCH·CLIMATE)이면 null
   */
  void saveCalls(Long runId, List<CallRecord> calls);

  Optional<RefreshRun> find(long runId);

  /** 끝난 실행 중 가장 최근 것 (SKIPPED 포함, TEST 제외) */
  Optional<RefreshRun> latestFinished();

  /**
   * 서버가 실행 도중 멈춰 RUNNING으로 남은 실행을 FAILED로 닫는다.
   *
   * @return 닫은 실행 수
   */
  int closeAbandoned(Instant startedBefore, Instant now);

  /**
   * startedBefore보다 먼저 시작한 실행과 그 호출 이력, 그 전에 부른 실행 밖 호출 이력을 지운다.
   *
   * @return 지운 실행 수
   */
  int purgeBefore(Instant startedBefore);
}
