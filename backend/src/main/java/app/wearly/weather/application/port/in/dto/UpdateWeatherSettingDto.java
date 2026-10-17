package app.wearly.weather.application.port.in.dto;

/** 스케줄 설정 바꾸기 (PUT, 전체 교체). 빠진 값은 필드 오류다 */
public record UpdateWeatherSettingDto(
    Boolean enabled,
    Integer intervalMinutes,
    Integer forecastDays,
    Integer failureThreshold,
    Integer cooldownMinutes) {}
