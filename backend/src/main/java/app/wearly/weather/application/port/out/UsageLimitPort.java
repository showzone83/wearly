package app.wearly.weather.application.port.out;

import java.time.LocalDate;

/** 회원별 하루 사용 횟수 (Valkey 카운터, 날짜는 한국 날짜). 좌표 예보(ON_DEMAND)·장소 검색(SEARCH)에 쓴다 */
public interface UsageLimitPort {

  /**
   * 한 번 쓴다. 이미 limit만큼 썼으면 세지 않고 false.
   *
   * @param kind ON_DEMAND · SEARCH
   */
  boolean tryConsume(String kind, long userId, LocalDate day, int limit);
}
