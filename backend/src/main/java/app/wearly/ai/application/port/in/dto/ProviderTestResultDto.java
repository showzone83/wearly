package app.wearly.ai.application.port.in.dto;

/** 관리자 시험 분석 결과 (07 문서 4장). 실패해도 ok=false로 돌려준다 */
public record ProviderTestResultDto(
    boolean ok,
    AiSuggestionDto suggestion,
    String errorCode,
    String errorMessage,
    int latencyMs,
    Long inputTokens,
    Long outputTokens,
    String model) {}
