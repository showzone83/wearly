package app.wearly.ai.application.port.in;

import app.wearly.ai.domain.AiSuggestion;

/** 관리자 시험 분석 결과 (07 문서 4장). 실패해도 HTTP 200, ok=false */
public record ProviderTestResult(
    boolean ok,
    AiSuggestion suggestion,
    String errorCode,
    String errorMessage,
    int latencyMs,
    Long inputTokens,
    Long outputTokens,
    String model) {}
