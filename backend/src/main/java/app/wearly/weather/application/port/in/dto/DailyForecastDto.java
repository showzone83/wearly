package app.wearly.weather.application.port.in.dto;

import app.wearly.weather.domain.DailyForecast;
import java.time.LocalDate;
import java.util.List;

/** 하루 예보 (유스케이스 출력). cond는 SUNNY · CLOUDY · RAIN */
public record DailyForecastDto(
    LocalDate date,
    double temp,
    double min,
    double max,
    int rainProb,
    String cond,
    String condText) {

  public static DailyForecastDto from(DailyForecast d) {
    return new DailyForecastDto(
        d.date(), d.temp(), d.min(), d.max(), d.rainProb(), d.cond().name(), d.condText());
  }

  public static List<DailyForecastDto> from(List<DailyForecast> days) {
    return days.stream().map(DailyForecastDto::from).toList();
  }
}
