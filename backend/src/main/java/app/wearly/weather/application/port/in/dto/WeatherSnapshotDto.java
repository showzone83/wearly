package app.wearly.weather.application.port.in.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 관리자 저장된 날씨 한 지역. 한 번도 받지 못한 지역은 current·fetchedAt이 null이다.
 *
 * @param raw 목록에서는 null, 상세에서만 공급자 원본 응답
 */
public record WeatherSnapshotDto(
    String regionCode,
    String regionLabel,
    boolean regionActive,
    String providerCode,
    CurrentWeatherDto current,
    List<DailyForecastDto> days,
    String locationName,
    Instant fetchedAt,
    boolean stale,
    String lastError,
    Instant lastErrorAt,
    Map<String, Object> raw) {}
