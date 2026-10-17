package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;

/**
 * 키가 유효한지 각 사의 모델 목록 조회로 확인한다 (사진 분석보다 싸다). Claude GET /v1/models, OpenAI GET /v1/models, Gemini
 * GET /v1beta/models
 */
public interface ApiKeyVerifierPort {

  /**
   * @throws AiProviderException 거부·오류·시간 초과. 메시지에는 키 조각을 남기지 않는다
   */
  void verify(AiProviderSetting setting, String apiKey);
}
