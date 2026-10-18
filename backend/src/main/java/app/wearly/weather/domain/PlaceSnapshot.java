package app.wearly.weather.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * REGION 밖 장소(격자)의 최신 예보 (tb_weather_place_snapshot). 좌표 요청 때 캐시가 오래됐으면 공급자를 한 번 불러 바꾼다. 받기에 실패하면
 * 이전 값은 두고 lastError만 바꾼다.
 *
 * @param label 공급자가 알려 준 장소 이름 (없으면 null)
 * @param fetchedAt 마지막으로 받은 시각. 한 번도 받지 못했으면 null
 */
public record PlaceSnapshot(
    String gridKey,
    double lat,
    double lon,
    String label,
    String providerCode,
    CurrentWeather current,
    List<DailyForecast> days,
    Instant fetchedAt,
    String lastError,
    Instant lastErrorAt) {

  public PlaceSnapshot {
    days = days == null ? List.of() : List.copyOf(days);
  }

  public static PlaceSnapshot of(
      GridKey grid, String label, String providerCode, WeatherReport report, Instant now) {
    return new PlaceSnapshot(
        grid.key(),
        grid.lat(),
        grid.lon(),
        label,
        providerCode,
        report.current(),
        report.days(),
        now,
        null,
        null);
  }

  public boolean hasData() {
    return fetchedAt != null && current != null;
  }

  /** 받은 지 ttl이 지나지 않았는지 */
  public boolean fresh(Instant now, Duration ttl) {
    return hasData() && fetchedAt.plus(ttl).isAfter(now);
  }

  public List<DailyForecast> daysFrom(LocalDate from) {
    return days.stream().filter(d -> !d.date().isBefore(from)).toList();
  }
}
