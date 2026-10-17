package app.wearly.ai;

/** 계약 05 6장 AnalyzeResponse. provider는 공급자 ID 문자열(예: "claude", "openai", "mock") */
public record AnalyzeResponse(AiSuggestion suggestion, String provider, String model, int remainingToday) {
}
