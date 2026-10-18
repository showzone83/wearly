package app.wearly.weather.domain;

import java.time.LocalDate;

/**
 * 하루 예보 (공급자와 상관없는 모양). 날짜는 그 지역의 현지 날짜, 기온은 섭씨.
 *
 * @param temp 평균 기온
 * @param rainProb 강수(비·눈) 확률 0~100
 */
public record DailyForecast(
    LocalDate date,
    double temp,
    double min,
    double max,
    int rainProb,
    WeatherCondition cond,
    String condText) {}
