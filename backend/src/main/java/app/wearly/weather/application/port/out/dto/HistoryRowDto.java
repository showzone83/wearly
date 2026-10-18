package app.wearly.weather.application.port.out.dto;

import java.time.Instant;
import java.time.LocalDate;

/** 날씨 이력 한 줄 (tb_weather_daily_history 그대로). 예보·관측이 없으면 그 칸은 null */
public record HistoryRowDto(
    String regionCode,
    LocalDate weatherDate,
    String providerCode,
    Double forecastTemp,
    Double forecastMin,
    Double forecastMax,
    Integer rainProb,
    String cond,
    String condText,
    Instant forecastFetchedAt,
    Double observedMin,
    Double observedMax,
    double observedTempSum,
    int observedCount,
    int observedRainCount,
    Instant firstObservedAt,
    Instant lastObservedAt) {}
