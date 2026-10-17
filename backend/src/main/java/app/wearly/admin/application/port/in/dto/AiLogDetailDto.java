package app.wearly.admin.application.port.in.dto;

import java.time.Instant;
import java.util.Map;

/** AI 호출 기록 하나 (유스케이스 출력). result는 저장된 결과 JSON (실패면 null) */
public record AiLogDetailDto(
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
    String imageSha256,
    String errorCode,
    Map<String, Object> result) {}
