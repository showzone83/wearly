package app.wearly.weather.application.port.in.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 날짜 하나의 날씨 (GET /weather/day).
 *
 * @param source FORECAST · CLIMATE
 * @param forecastUntil 이 장소의 예보가 있는 마지막 날짜. 예보가 없으면 null
 * @param fetchedAt FORECAST면 예보를 받은 시각, CLIMATE면 평년값을 계산한 시각
 * @param climate CLIMATE일 때만, 아니면 null
 */
public record WeatherDayDto(
    PlaceDto place,
    LocalDate date,
    String source,
    DayWeatherDto day,
    LocalDate forecastUntil,
    Instant fetchedAt,
    ClimateInfoDto climate) {

  public static final String FORECAST = "FORECAST";
  public static final String CLIMATE = "CLIMATE";
}
