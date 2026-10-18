package app.wearly.weather.adapter.out.persistence.jpa;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** tb_weather_daily_history 복합 키 (region_code, weather_date) */
public class WeatherDailyHistoryId implements Serializable {

  private String regionCode;
  private LocalDate weatherDate;

  protected WeatherDailyHistoryId() {}

  public WeatherDailyHistoryId(String regionCode, LocalDate weatherDate) {
    this.regionCode = regionCode;
    this.weatherDate = weatherDate;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof WeatherDailyHistoryId other
        && Objects.equals(regionCode, other.regionCode)
        && Objects.equals(weatherDate, other.weatherDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(regionCode, weatherDate);
  }
}
