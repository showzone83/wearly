package app.wearly.weather.application.port.in.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 지역·날짜별 날씨 이력 한 줄 (17 문서 6장).
 *
 * @param regionLabel 지역 이름. 코드에서 지워진 지역이면 null
 * @param forecast 그날 마지막으로 받은 예보. 없으면 null
 * @param observed 주기마다 받은 현재 날씨 요약. 관측이 없으면 null
 */
public record WeatherHistoryDto(
    String regionCode,
    String regionLabel,
    LocalDate date,
    String providerCode,
    DailyForecastDto forecast,
    Instant forecastFetchedAt,
    ObservedDto observed,
    Instant firstObservedAt,
    Instant lastObservedAt) {

  /**
   * 실측 요약. 기온은 섭씨
   *
   * @param rainCount 현재 상태가 RAIN이었던 관측 수
   */
  public record ObservedDto(double min, double max, double avg, int count, int rainCount) {}
}
