package app.wearly.weather.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.CurrentWeatherDto;
import app.wearly.weather.application.port.in.dto.DailyForecastDto;
import app.wearly.weather.application.port.in.dto.WeatherViewDto;
import java.util.List;

/**
 * GET /weather 응답 (17 문서 5장). 기온은 섭씨(소수 첫째 자리), 날짜는 YYYY-MM-DD, 시각은 ISO-8601 UTC. 공급자 이름은 넣지 않는다.
 */
public record WeatherResponse(
    Region region, Current current, List<Day> days, String fetchedAt, boolean stale) {

  public record Region(String code, String label) {}

  public record Current(
      double temp, Double feelsLike, String cond, String condText, String observedAt) {

    public static Current from(CurrentWeatherDto c) {
      return c == null
          ? null
          : new Current(
              round(c.temp()),
              c.feelsLike() == null ? null : round(c.feelsLike()),
              c.cond(),
              c.condText(),
              Times.iso(c.observedAt()));
    }
  }

  public record Day(
      String date,
      double temp,
      double min,
      double max,
      int rainProb,
      String cond,
      String condText) {

    public static Day from(DailyForecastDto d) {
      return new Day(
          d.date().toString(),
          round(d.temp()),
          round(d.min()),
          round(d.max()),
          d.rainProb(),
          d.cond(),
          d.condText());
    }

    public static List<Day> from(List<DailyForecastDto> days) {
      return days.stream().map(Day::from).toList();
    }
  }

  public static WeatherResponse from(WeatherViewDto v) {
    return new WeatherResponse(
        new Region(v.regionCode(), v.regionLabel()),
        Current.from(v.current()),
        Day.from(v.days()),
        Times.iso(v.fetchedAt()),
        v.stale());
  }

  /** 소수 첫째 자리 */
  public static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
