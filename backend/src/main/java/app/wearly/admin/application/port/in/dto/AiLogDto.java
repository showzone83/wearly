package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/** AI 호출 기록 목록의 한 줄 (유스케이스 출력). resultSummary는 결과 요약 문구 (실패면 null) */
public record AiLogDto(
    Long id,
    Instant createdAt,
    Long userId,
    String userEmail,
    String feature,
    String providerId,
    String model,
    String status,
    int latencyMs,
    Integer inputTokens,
    Integer outputTokens,
    Integer imageBytes,
    String resultSummary,
    String errorCode) {}
