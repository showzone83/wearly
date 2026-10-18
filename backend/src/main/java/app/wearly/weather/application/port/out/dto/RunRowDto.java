package app.wearly.weather.application.port.out.dto;

import java.time.Instant;

/** 실행 목록 한 줄 (요청한 관리자 이메일 포함) */
public record RunRowDto(
    Long runId,
    String triggerType,
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
