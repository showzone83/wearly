package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/**
 * 호출 이력 한 줄.
 *
 * @param runId 실행 밖 호출(ON_DEMAND·SEARCH·CLIMATE)이면 null (trigger도 null)
 * @param regionCode REGION 코드, 격자 키(ON_DEMAND·CLIMATE), 검색이면 null
 */
public record WeatherCallDto(
    long callId,
    Long runId,
    String trigger,
    String regionCode,
    String providerCode,
    String callKind,
    boolean fallback,
    Integer httpStatus,
    boolean success,
    String errorCode,
    String errorMessage,
    long durationMs,
    Integer responseBytes,
    Instant calledAt) {}
