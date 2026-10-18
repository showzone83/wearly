package app.wearly.weather.application.port.in;

import java.time.Instant;

/** 서버 주기 실행 (스케줄러가 부른다, 17 문서 3장) */
public interface WeatherScheduleUseCase {

  /**
   * 다음 실행 시각. DB 설정을 매번 다시 읽으므로 주기를 바꾸면 재시작 없이 반영된다.
   *
   * @param lastFinished 직전 실행이 끝난 시각. 서버 시작 직후면 null
   */
  Instant nextRunAt(Instant lastFinished);

  /**
   * 한 번 실행한다. 꺼져 있거나 공급자가 없거나 다른 실행 중이면 SKIPPED로 남긴다.
   *
   * @param startup 서버 시작 직후 실행. 저장된 값이 주기보다 새것이면 아무것도 하지 않는다
   */
  void runScheduled(boolean startup);

  /** 보관 기간이 지난 실행·호출 이력을 지운다 */
  void purgeOldLogs();
}
