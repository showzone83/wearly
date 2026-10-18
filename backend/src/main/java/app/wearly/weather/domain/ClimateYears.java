package app.wearly.weather.domain;

import java.time.LocalDate;

/**
 * 평년값을 계산할 기간: 오늘(한국 날짜) 기준 지난 N개 완결 연도. 예: 2026년, 10년 → 2016-01-01 ~ 2025-12-31, "2016-2025".
 *
 * @param label tb_weather_climate.years와 응답 climate.years
 */
public record ClimateYears(LocalDate from, LocalDate to, String label) {

  public static ClimateYears of(LocalDate today, int years) {
    int last = today.getYear() - 1;
    int first = last - Math.max(1, years) + 1;
    return new ClimateYears(
        LocalDate.of(first, 1, 1), LocalDate.of(last, 12, 31), first + "-" + last);
  }
}
