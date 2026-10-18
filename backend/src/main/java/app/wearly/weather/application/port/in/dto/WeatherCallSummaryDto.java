package app.wearly.weather.application.port.in.dto;

import java.util.List;

/**
 * 호출 이력 요약: 오늘(한국 시간) 호출·실패·예비 전환·평균 소요 시간 + 공급자별 이번 달 사용량.
 *
 * @param avgDurationMs 오늘 호출이 없으면 null
 */
public record WeatherCallSummaryDto(
    long todayCalls,
    long todayFailures,
    long todayFallbacks,
    Double avgDurationMs,
    List<MonthUsageDto> months,
    List<AuxiliaryUsageDto> auxiliaryMonths) {

  /**
   * 공급자 한 곳의 이번 달 사용량.
   *
   * @param quota null이면 한도 없음
   */
  public record MonthUsageDto(String providerCode, String displayName, long used, Integer quota) {}

  /**
   * 공급자 표(tb_weather_provider) 밖 보조 공급자 한 곳의 이번 달 호출 수 (실패 포함, 한도 없음).
   * OPEN_METEO(평년값)·OPEN_METEO_GEO(장소 검색)는 늘 있고(0 포함), 그 밖에 이력에만 있는 공급자 코드가 뒤에 붙는다.
   */
  public record AuxiliaryUsageDto(String providerCode, String displayName, long used) {}
}
