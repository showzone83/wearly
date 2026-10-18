package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.WeatherHistoryDto;
import app.wearly.weather.application.port.in.dto.WeatherHistoryDto.ObservedDto;

/**
 * 지역·날짜별 날씨 이력 한 줄 (17 문서 6장). forecast는 그날 마지막으로 받은 예보, observed는 주기마다 받은 현재 날씨 요약이다. 없으면 null.
 */
public record WeatherHistoryResponse(
    String regionCode,
    String regionLabel,
    String date,
    String providerCode,
    WeatherDayResponse forecast,
    String forecastFetchedAt,
    Observed observed,
    String firstObservedAt,
    String lastObservedAt) {

  /** 실측 요약. rainCount = 현재 상태가 RAIN이었던 관측 수 */
  public record Observed(double min, double max, double avg, int count, int rainCount) {

    static Observed from(ObservedDto o) {
      return o == null
          ? null
          : new Observed(
              WeatherDayResponse.round(o.min()),
              WeatherDayResponse.round(o.max()),
              WeatherDayResponse.round(o.avg()),
              o.count(),
              o.rainCount());
    }
  }

  public static WeatherHistoryResponse from(WeatherHistoryDto h) {
    return new WeatherHistoryResponse(
        h.regionCode(),
        h.regionLabel(),
        h.date().toString(),
        h.providerCode(),
        WeatherDayResponse.from(h.forecast()),
        Times.iso(h.forecastFetchedAt()),
        Observed.from(h.observed()),
        Times.iso(h.firstObservedAt()),
        Times.iso(h.lastObservedAt()));
  }
}
