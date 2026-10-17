package app.wearly.admin.application.port.out.dto;

import java.time.Instant;
import java.util.Map;

/** AI 호출 기록 한 행 (회원 이메일 포함). result는 결과 JSON (실패면 null) */
public record AiLogRowDto(
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
    Map<String, Object> result,
    String summary) {}
