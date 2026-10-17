package app.wearly.ai.domain;

/** 공급자 호출 결과. model은 실제로 응답한 모델 (폴백 시 설정값과 다를 수 있다) */
public record AiResult(AiSuggestion suggestion, String model, Long inputTokens, Long outputTokens) {
}
