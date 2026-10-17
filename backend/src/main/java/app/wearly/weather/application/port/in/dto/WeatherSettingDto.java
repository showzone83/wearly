package app.wearly.weather.application.port.in.dto;

import app.wearly.weather.domain.WeatherSetting;
import java.time.Instant;

/** 스케줄 설정 */
public record WeatherSettingDto(
    boolean enabled,
    int intervalMinutes,
    int forecastDays,
    int failureThreshold,
    int cooldownMinutes,
    Long updatedBy,
    Instant updatedAt) {

  public static WeatherSettingDto from(WeatherSetting s) {
    return new WeatherSettingDto(
        s.enabled(),
        s.intervalMinutes(),
        s.forecastDays(),
        s.failureThreshold(),
        s.cooldownMinutes(),
        s.updatedBy(),
        s.updatedAt());
  }
}
