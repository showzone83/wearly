package app.wearly.admin.adapter.in.web.request;

import app.wearly.weather.application.port.in.dto.UpdateWeatherSettingDto;

/** PUT /admin/weather/settings 본문 (17 문서 6장). 필수·범위 검증은 유스케이스가 한다 (fieldErrors) */
public record WeatherSettingRequest(
    Boolean enabled,
    Integer intervalMinutes,
    Integer forecastDays,
    Integer failureThreshold,
    Integer cooldownMinutes) {

  public UpdateWeatherSettingDto toDto() {
    return new UpdateWeatherSettingDto(
        enabled, intervalMinutes, forecastDays, failureThreshold, cooldownMinutes);
  }
}
