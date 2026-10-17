package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderSetting;
import java.util.List;

/**
 * 제공사의 모델 목록 API를 부른다 (관리자 화면 모델 드롭다운). Claude GET /v1/models(SDK models().list()), OpenAI GET
 * /v1/models, Gemini GET /v1beta/models. 사진 분석에 맞지 않는 모델은 어댑터가 ModelListRules로 뺀다.
 */
public interface AiModelCatalogPort {

  /**
   * @param apiKey 고른 키 (DB 또는 서버 설정). 로그·예외에 남기지 않는다
   * @return 추천 표시 없는 목록 (순서는 상관없다)
   * @throws AiProviderException 거부·오류·시간 초과·응답 형식 오류. reason은
   *     API_&lt;상태&gt;·TIMEOUT·API_CLIENT·BAD_RESPONSE
   */
  List<AiModelEntry> listModels(AiProviderSetting setting, String apiKey);
}
