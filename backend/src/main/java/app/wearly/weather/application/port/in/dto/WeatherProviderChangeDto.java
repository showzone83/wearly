package app.wearly.weather.application.port.in.dto;

/** 공급자 변경 전후 (변경 이력용) */
public record WeatherProviderChangeDto(WeatherProviderDto before, WeatherProviderDto after) {}
