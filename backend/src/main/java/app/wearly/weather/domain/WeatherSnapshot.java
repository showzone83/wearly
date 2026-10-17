package app.wearly.weather.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 지역별 최신 날씨 (tb_weather_snapshot). 받기에 실패하면 이전 값은 두고 lastError만 바꾼다.
 *
 * @param providerCode 이 값을 준 공급자. 한 번도 받지 못했으면 null
 * @param fetchedAt 마지막으로 받은 시각. 한 번도 받지 못했으면 null
 */
public record WeatherSnapshot(
    String regionCode,
    String providerCode,
    CurrentWeather current,
    List<DailyForecast> days,
    Map<String, Object> raw,
    String locationName,
    Instant fetchedAt,
    String lastError,
    Instant lastErrorAt) {

  public WeatherSnapshot {
    days = days == null ? List.of() : List.copyOf(days);
    raw = raw == null ? Map.of() : raw;
  }

  public static WeatherSnapshot of(
      String regionCode, String providerCode, WeatherReport report, Instant now) {
    return new WeatherSnapshot(
        regionCode,
        providerCode,
        report.current(),
        report.days(),
        report.raw(),
        report.locationName(),
        now,
        null,
        null);
  }

  public boolean hasData() {
    return fetchedAt != null && current != null;
  }

  /** from(그 지역의 오늘) 이후 예보만. 자정을 넘긴 뒤 아직 새로 받지 못했을 때 어제 칸을 빼려고 쓴다 */
  public List<DailyForecast> daysFrom(LocalDate from) {
    return days.stream().filter(d -> !d.date().isBefore(from)).toList();
  }
}
