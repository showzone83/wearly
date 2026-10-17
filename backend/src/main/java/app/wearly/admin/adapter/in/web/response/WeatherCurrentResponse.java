package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.CurrentWeatherDto;

/** 관리자 날씨 화면의 지금 날씨. 앱 응답(GET /weather의 current)과 같은 모양 */
public record WeatherCurrentResponse(
    double temp, Double feelsLike, String cond, String condText, String observedAt) {

  public static WeatherCurrentResponse from(CurrentWeatherDto c) {
    return c == null
        ? null
        : new WeatherCurrentResponse(
            WeatherDayResponse.round(c.temp()),
            c.feelsLike() == null ? null : WeatherDayResponse.round(c.feelsLike()),
            c.cond(),
            c.condText(),
            Times.iso(c.observedAt()));
  }
}
