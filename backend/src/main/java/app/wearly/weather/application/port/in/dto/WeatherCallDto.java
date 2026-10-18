package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/** 호출 이력 한 줄 */
public record WeatherCallDto(
    long callId,
    long runId,
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
