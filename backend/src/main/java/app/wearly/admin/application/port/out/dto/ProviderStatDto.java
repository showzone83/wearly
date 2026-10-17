package app.wearly.admin.application.port.out.dto;

/** 공급자별 호출 집계. avgLatencyMs는 호출이 없으면 null, 토큰 합은 없으면 0 */
public record ProviderStatDto(
    String providerId,
    long success,
    long failed,
    Long avgLatencyMs,
    long inputTokens,
    long outputTokens) {}
