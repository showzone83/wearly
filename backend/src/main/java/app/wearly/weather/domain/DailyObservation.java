package app.wearly.weather.domain;

import java.time.LocalDate;

/**
 * 과거 하루 관측값 (평년값 계산용). 자료가 없는 칸은 null.
 *
 * @param max 최고 기온 (섭씨)
 * @param min 최저 기온 (섭씨)
 * @param precipitation 강수량 (mm)
 */
public record DailyObservation(LocalDate date, Double max, Double min, Double precipitation) {}
