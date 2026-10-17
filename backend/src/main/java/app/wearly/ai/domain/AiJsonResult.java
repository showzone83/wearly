package app.wearly.ai.domain;

import java.util.Map;

/**
 * 구조화 JSON 생성 결과. json은 공급자 응답 텍스트를 JSON 객체로 읽은 값 (Map·List·String·Number·Boolean·null). model은 실제로
 * 응답한 모델
 */
public record AiJsonResult(
    Map<String, Object> json, String model, Long inputTokens, Long outputTokens) {}
