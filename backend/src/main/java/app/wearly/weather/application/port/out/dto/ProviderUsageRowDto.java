package app.wearly.weather.application.port.out.dto;

/** 공급자별 호출 수 */
public record ProviderUsageRowDto(String providerCode, long calls) {}
