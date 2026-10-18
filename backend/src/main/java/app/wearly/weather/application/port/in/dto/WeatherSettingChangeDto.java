package app.wearly.weather.application.port.in.dto;

/** 설정 변경 전후 (변경 이력용). 값이 같으면 changed = false */
public record WeatherSettingChangeDto(
    WeatherSettingDto before, WeatherSettingDto after, boolean changed) {}
