package app.wearly.weather.application.port.in.dto;

import app.wearly.weather.domain.CurrentWeather;
import java.time.Instant;

/** 지금 날씨 (유스케이스 출력). cond는 SUNNY · CLOUDY · RAIN */
public record CurrentWeatherDto(
    double temp, Double feelsLike, String cond, String condText, Instant observedAt) {

  public static CurrentWeatherDto from(CurrentWeather c) {
    return c == null
        ? null
        : new CurrentWeatherDto(
            c.temp(), c.feelsLike(), c.cond().name(), c.condText(), c.observedAt());
  }
}
