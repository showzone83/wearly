package app.wearly.weather.domain;

import java.time.Instant;

/**
 * 지금 날씨 (공급자와 상관없는 모양). 기온은 섭씨.
 *
 * @param feelsLike 체감 온도. 공급자가 주지 않으면 null
 * @param condText 공급자가 준 한국어 설명 (예: 맑음, 가벼운 비)
 * @param observedAt 공급자가 관측·갱신한 시각
 */
public record CurrentWeather(
    double temp, Double feelsLike, WeatherCondition cond, String condText, Instant observedAt) {}
