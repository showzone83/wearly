package app.wearly.ai.application.port.in;

import app.wearly.ai.domain.AiSuggestion;

/** 05 문서 6장 AnalyzeResponse */
public record AnalyzeResult(
    AiSuggestion suggestion, String provider, String model, int remainingToday) {}
