package app.wearly.admin.adapter.in.web.response;

import app.wearly.weather.application.port.in.dto.WeatherCallSummaryDto;
import java.util.List;

/** GET /admin/weather/calls/summary: 오늘(한국 시간) 호출 요약 + 공급자별 이번 달 사용량 (17 문서 6장) */
public record WeatherCallSummaryResponse(Today today, List<Month> months) {

  /**
   * 오늘 호출 집계.
   *
   * @param avgDurationMs 오늘 호출이 없으면 null (정수 밀리초로 반올림)
   */
  public record Today(long calls, long failures, long fallbacks, Long avgDurationMs) {}

  /**
   * 공급자 한 곳의 이번 달 사용량.
   *
   * @param quota null이면 한도 없음
   */
  public record Month(String providerCode, String displayName, long used, Integer quota) {}

  public static WeatherCallSummaryResponse from(WeatherCallSummaryDto d) {
    return new WeatherCallSummaryResponse(
        new Today(
            d.todayCalls(),
            d.todayFailures(),
            d.todayFallbacks(),
            d.avgDurationMs() == null ? null : Math.round(d.avgDurationMs())),
        d.months().stream()
            .map(m -> new Month(m.providerCode(), m.displayName(), m.used(), m.quota()))
            .toList());
  }
}
