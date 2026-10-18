package app.wearly.weather.application.port.out.dto;

import java.time.Instant;

/** 호출 이력 한 줄 (그 실행의 트리거 포함) */
public record CallRowDto(
    Long callId,
    Long runId,
    String triggerType,
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
