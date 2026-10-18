package app.wearly.weather.application.port.in.dto;

/**
 * 평년값 정보 (CLIMATE일 때만).
 *
 * @param years 계산에 쓴 연도 (예: "2016-2025")
 * @param rainDayRatio 강수 1mm 이상인 날의 비율 (0~1)
 */
public record ClimateInfoDto(String years, double rainDayRatio) {}
