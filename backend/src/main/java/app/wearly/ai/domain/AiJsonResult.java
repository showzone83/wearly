package app.wearly.ai.domain;

import java.util.Map;

/**
 * 구조화 JSON 생성 결과. json은 공급자 응답 텍스트를 JSON 객체로 읽은 값 (Map·List·String·Number·Boolean·null). model은 실제로
 * 응답한 모델.
 *
 * <p>토큰 수는 공급자가 주지 않으면 null이다.
 *
 * @param inputTokens 입력 토큰 전체 (캐시 읽기·쓰기 포함). Claude는 usage의 input_tokens +
 *     cache_creation_input_tokens + cache_read_input_tokens, OpenAI는 usage.input_tokens, Gemini는
 *     usageMetadata.promptTokenCount
 * @param cacheReadTokens 입력 중 캐시에서 읽은 토큰. Claude cache_read_input_tokens, OpenAI
 *     usage.input_tokens_details.cached_tokens, Gemini usageMetadata.cachedContentTokenCount
 * @param cacheWriteTokens 입력 중 캐시에 새로 쓴 토큰. Claude cache_creation_input_tokens만 있다 (OpenAI·Gemini는
 *     자동 캐시라 쓰기 요금이 따로 없어 null)
 */
public record AiJsonResult(
    Map<String, Object> json,
    String model,
    Long inputTokens,
    Long outputTokens,
    Long cacheReadTokens,
    Long cacheWriteTokens) {

  /** 캐시 토큰을 모를 때 */
  public AiJsonResult(Map<String, Object> json, String model, Long inputTokens, Long outputTokens) {
    this(json, model, inputTokens, outputTokens, null, null);
  }
}
