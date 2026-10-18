package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/**
 * 관리자 공급자 한 줄: 설정 + 상태 + 이번 달(한국 시간) 사용량.
 *
 * @param usable 지금 실행하면 부를 수 있는지 (켜짐·키 있음·차단 아님·한도 이내)
 * @param callsPerFetch 한 지역을 받는 데 드는 호출 수 (예상 호출 수 계산용)
 */
public record WeatherProviderDto(
    String code,
    String displayName,
    boolean enabled,
    int priority,
    String keyName,
    String keyState,
    String status,
    int consecutiveFailures,
    Instant circuitOpenUntil,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    String lastError,
    long monthUsage,
    Integer monthlyQuota,
    int maxForecastDays,
    int callsPerFetch,
    boolean usable,
    Long updatedBy,
    Instant updatedAt) {}
