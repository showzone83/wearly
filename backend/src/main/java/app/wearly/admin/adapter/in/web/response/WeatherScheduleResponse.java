package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.WeatherScheduleDto;
import app.wearly.weather.application.port.in.dto.WeatherSettingDto;

/** GET·PUT /admin/weather/settings (17 문서 6장) */
public record WeatherScheduleResponse(
    Setting setting,
    String nextRunAt,
    boolean running,
    WeatherRunResponse lastRun,
    int activeRegions,
    boolean schedulerEnabled) {

  public record Setting(
      boolean enabled,
      int intervalMinutes,
      int forecastDays,
      int failureThreshold,
      int cooldownMinutes,
      String updatedBy,
      String updatedAt) {

    static Setting from(WeatherSettingDto s) {
      return new Setting(
          s.enabled(),
          s.intervalMinutes(),
          s.forecastDays(),
          s.failureThreshold(),
          s.cooldownMinutes(),
          Ids.format(s.updatedBy()),
          Times.iso(s.updatedAt()));
    }
  }

  public static WeatherScheduleResponse from(WeatherScheduleDto d) {
    return new WeatherScheduleResponse(
        Setting.from(d.setting()),
        Times.iso(d.nextRunAt()),
        d.running(),
        WeatherRunResponse.from(d.lastRun()),
        d.activeRegions(),
        d.schedulerEnabled());
  }
}
