package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.ai.domain.AiSuggestionValidator;
import app.wearly.ai.domain.AnalysisPrompt;
import app.wearly.common.domain.code.CodeSnapshot;

/**
 * AI 공급자 (출력 포트). 공급자는 "이미지(선택) + 지시문 + 스키마 → JSON 객체"만 한다 (M1). 무엇을 묻고 어떻게 검증할지(사진 분석 프롬프트· 코드 검증,
 * 코디 추천 후보 검증)는 공급자 밖(도메인·호출한 쪽)에 있다. 구현은 출력 어댑터의 Claude·OpenAI·Gemini(tb_ai_provider 행마다 하나)와 목업.
 */
public interface AiModelPort {

  /** 응답·로그에 쓰는 공급자 ID (tb_ai_provider.provider_id, 목업은 "mock") */
  String name();

  /** 설정된 모델 ID. 목업은 null */
  String model();

  /**
   * 구조화 JSON을 만든다. 내용 검증은 호출한 쪽이 한다.
   *
   * @param image 함께 보낼 사진. 없으면 null
   * @throws AiProviderException 공급자 오류, 시간 초과, 거절, 잘림, 받지 않는 사진 형식, JSON 객체가 아닌 응답
   */
  AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image);

  /** 이미지 없는 구조화 JSON */
  default AiJsonResult generateJson(AiJsonPrompt prompt) {
    return generateJson(prompt, null);
  }

  /**
   * 사진 분석 = 사진 분석 프롬프트(도메인)로 JSON을 받고 도메인 규칙으로 검증한다. 공급자는 옷장 규칙을 모른다.
   *
   * @throws AiProviderException 공급자 오류, 또는 규칙에 맞지 않는 응답(INVALID_OUTPUT)
   */
  default AiResult analyze(AiImage image, CodeSnapshot codes) {
    AiJsonResult r = generateJson(AnalysisPrompt.request(image, codes), image);
    return new AiResult(
        AiSuggestionValidator.validate(r.json(), codes),
        r.model(),
        r.inputTokens(),
        r.outputTokens());
  }
}
