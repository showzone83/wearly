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
    List<MonthUsageDto> months) {

  /**
   * 공급자 한 곳의 이번 달 사용량.
   *
   * @param quota null이면 한도 없음
   */
  public record MonthUsageDto(String providerCode, String displayName, long used, Integer quota) {}
}
