package app.wearly.weather.domain;

import app.wearly.common.domain.Zones;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 지역·날짜별 날씨 이력 (tb_weather_daily_history, 17 문서 2장). 받기에 성공할 때마다 그날 행을 고치고, 날짜가 지나면 더 바뀌지 않아 그대로 지난
 * 날씨가 된다. AI 코디 검증처럼 지난 날짜의 날씨가 필요할 때 쓴다.
 *
 * @param date 한국 날짜
 * @param providerCode 마지막으로 예보를 준 공급자. 예보를 받은 적 없으면 null
 * @param forecast 그날 마지막으로 받은 일별 예보. 없으면 null
 * @param forecastFetchedAt 그 예보를 받은 시각
 * @param observedMin 주기마다 받은 현재 기온 중 최저. 관측이 없으면 null
 * @param observedTempSum 현재 기온 합 (평균 = 합 / 관측 수)
 * @param observedRainCount 현재 상태가 RAIN이었던 관측 수
 * @param lastObservedAt 마지막으로 더한 관측 시각. 이보다 늦은 관측만 더한다 (같은 관측을 두 번 세지 않게)
 */
public record WeatherHistory(
    String regionCode,
    LocalDate date,
    String providerCode,
    DailyForecast forecast,
    Instant forecastFetchedAt,
    Double observedMin,
    Double observedMax,
    double observedTempSum,
    int observedCount,
    int observedRainCount,
    Instant firstObservedAt,
    Instant lastObservedAt) {

  public static WeatherHistory empty(String regionCode, LocalDate date) {
    return new WeatherHistory(regionCode, date, null, null, null, null, null, 0, 0, 0, null, null);
  }

  /** 관측 시각. 공급자가 주지 않았으면 받은 시각으로 본다 */
  public static Instant observedAt(CurrentWeather c, Instant fetchedAt) {
    return c.observedAt() == null ? fetchedAt : c.observedAt();
  }

  /** 그 시각이 속한 이력 날짜 (한국 날짜). 23:55 관측을 00:05에 받아도 전날 행에 들어간다 */
  public static LocalDate dateOf(Instant at) {
    return LocalDate.ofInstant(at, Zones.SEOUL);
  }

  /** 예보 칸을 새 값으로 바꾼다 (그날 마지막으로 받은 예보가 남는다) */
  public WeatherHistory withForecast(String providerCode, DailyForecast day, Instant fetchedAt) {
    return new WeatherHistory(
        regionCode,
        date,
        providerCode,
        day,
        fetchedAt,
        observedMin,
        observedMax,
        observedTempSum,
        observedCount,
        observedRainCount,
        firstObservedAt,
        lastObservedAt);
  }

  /** 관측 한 번을 실측 요약에 더한다. 이미 더한 관측보다 늦지 않으면 그대로 돌려준다 */
  public WeatherHistory observe(CurrentWeather c, Instant at) {
    if (lastObservedAt != null && !at.isAfter(lastObservedAt)) {
      return this;
    }
    return new WeatherHistory(
        regionCode,
        date,
        providerCode,
        forecast,
        forecastFetchedAt,
        observedMin == null ? c.temp() : Math.min(observedMin, c.temp()),
        observedMax == null ? c.temp() : Math.max(observedMax, c.temp()),
        observedTempSum + c.temp(),
        observedCount + 1,
        observedRainCount + (c.cond() == WeatherCondition.RAIN ? 1 : 0),
        firstObservedAt == null ? at : firstObservedAt,
        at);
  }

  /** 실측 평균 기온. 관측이 없으면 null */
  public Double observedAvg() {
    return observedCount == 0 ? null : observedTempSum / observedCount;
  }
}
