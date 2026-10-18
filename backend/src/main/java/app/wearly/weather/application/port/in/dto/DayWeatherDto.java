package app.wearly.weather.application.port.in.dto;

/**
 * 하루 날씨 (FORECAST면 예보, CLIMATE면 평년값). 기온은 섭씨.
 *
 * @param temp FORECAST 평균 기온, CLIMATE (최고+최저)/2의 평균
 * @param rainProb FORECAST 강수 확률, CLIMATE 비 온 날 비율×100 (0~100)
 * @param cond SUNNY · CLOUDY · RAIN. CLIMATE면 null
 * @param condText 공급자 설명. CLIMATE면 null
 */
public record DayWeatherDto(
    double temp, double min, double max, int rainProb, String cond, String condText) {}
