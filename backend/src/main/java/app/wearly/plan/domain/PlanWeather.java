package app.wearly.plan.domain;

import java.time.Instant;

/**
 * 예정일 날씨 (앱 Weather 모양 + 출처·확인 시각). DB에는 JSONB 한 칸으로 둔다.
 *
 * @param manual 사용자가 체감 온도를 직접 골랐는지
 * @param source FORECAST·CLIMATE·MANUAL 또는 null(예전 앱 데이터)
 * @param checkedAt 서버에서 날씨를 마지막으로 받은(확인한) 시각 또는 null
 */
public record PlanWeather(
    double temp,
    double min,
    double max,
    double rainProb,
    String cond,
    boolean manual,
    String source,
    Instant checkedAt) {}
