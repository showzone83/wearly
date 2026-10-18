package app.wearly.weather.domain;

import java.time.Duration;

/**
 * 장소별 날씨 고정값 (서버 설정 wearly.weather.*, 19 문서).
 *
 * @param placeCacheTtl REGION 밖 장소 예보 캐시를 그대로 쓰는 시간 (place-cache-ttl, 기본 3시간)
 * @param onDemandDailyLimit 회원마다 하루(한국 날짜)에 공급자를 실제로 부르게 할 수 있는 좌표 요청 수 (on-demand-daily-limit, 기본
 *     30)
 * @param searchCacheTtl 같은 검색어 결과를 그대로 쓰는 시간 (search-cache-ttl, 기본 1일)
 * @param searchDailyLimit 회원마다 하루에 공급자를 실제로 부르게 할 수 있는 검색 수 (search-daily-limit, 기본 100)
 * @param climateEnabled 평년값(Open-Meteo) 사용 여부 (climate.enabled). false면 CLIMATE를 주지 않는다
 * @param climateYears 평년값에 쓰는 지난 완결 연도 수 (climate.years, 기본 10)
 * @param lockWait 같은 격자를 다른 요청이 받고 있을 때 기다리는 최대 시간
 */
public record PlaceWeatherOptions(
    Duration placeCacheTtl,
    int onDemandDailyLimit,
    Duration searchCacheTtl,
    int searchDailyLimit,
    boolean climateEnabled,
    int climateYears,
    Duration lockWait) {

  /** 격자 잠금 유지 시간 (요청이 멈춰도 이 시간이 지나면 풀린다. 공급자 시간 초과보다 길게) */
  public static final Duration LOCK_TTL = Duration.ofSeconds(60);

  /** 검색 결과 최대 개수 */
  public static final int MAX_SEARCH_RESULTS = 10;

  /** 검색어 최대 길이 */
  public static final int MAX_QUERY = 50;

  /** 국내 검색 결과에 REGION 코드를 붙이는 거리 (km) */
  public static final double REGION_NEAR_KM = 30;

  /** 날짜는 오늘부터 이 기간 안 */
  public static final int MAX_DAYS_AHEAD_YEARS = 1;
}
