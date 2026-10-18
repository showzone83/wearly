package app.wearly.admin.adapter.in.web.response;

import app.wearly.weather.application.port.in.dto.DailyForecastDto;
import java.util.List;

/** 관리자 날씨 화면의 하루 예보. 앱 응답(GET /weather의 days)과 같은 모양 */
public record WeatherDayResponse(
    String date, double temp, double min, double max, int rainProb, String cond, String condText) {

  public static List<WeatherDayResponse> from(List<DailyForecastDto> days) {
    return days == null
        ? List.of()
        : days.stream()
            .map(
                d ->
                    new WeatherDayResponse(
                        d.date().toString(),
                        round(d.temp()),
                        round(d.min()),
                        round(d.max()),
                        d.rainProb(),
                        d.cond(),
                        d.condText()))
            .toList();
  }

  static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
