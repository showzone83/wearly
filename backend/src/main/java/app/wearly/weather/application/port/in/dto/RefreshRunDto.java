package app.wearly.weather.application.port.in.dto;

import java.time.Instant;

/** 실행 한 번 */
public record RefreshRunDto(
    long runId,
    String trigger,
    Long requestedBy,
    String requestedByEmail,
    String regionCode,
    String status,
    String skipReason,
    int successCount,
    int failCount,
    int fallbackCount,
    Instant startedAt,
    Instant finishedAt) {}
