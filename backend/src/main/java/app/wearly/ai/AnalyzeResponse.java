package app.wearly.ai;

/** 계약 6장 AnalyzeResponse */
public record AnalyzeResponse(AiSuggestion suggestion, String provider, String model, int remainingToday) {
}
