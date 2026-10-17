package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiResult;
import app.wearly.common.domain.code.CodeSnapshot;

/**
 * 옷 사진 분석 공급자 (출력 포트). 구현은 출력 어댑터의 ClaudeAiProvider·OpenAiProvider·GeminiAiProvider (tb_ai_provider
 * 행마다 하나)와 MockAiProvider(WEARLY_AI_PROVIDER=mock일 때만).
 */
public interface AiModelPort {

  /** 응답·로그에 쓰는 공급자 ID (tb_ai_provider.provider_id, 목업은 "mock") */
  String name();

  /** 설정된 모델 ID. 목업은 null */
  String model();

  /**
   * 사진을 분석해 활성 코드 안의 값으로 제안을 만든다. 허용 값 목록·스키마·검증은 codes로 한다.
   *
   * @throws AiProviderException 공급자 오류, 시간 초과, 거절, 허용 목록 밖 응답
   */
  AiResult analyze(AiImage image, CodeSnapshot codes);

  /**
   * 이미지 없이 프롬프트와 JSON 스키마로 구조화 JSON을 만든다 (사진 분석과 같은 구조화 출력 방식). 내용 검증은 호출한 쪽이 한다.
   *
   * @throws AiProviderException 공급자 오류, 시간 초과, 거절, 잘림, JSON 객체가 아닌 응답
   */
  AiJsonResult generateJson(AiJsonPrompt prompt);
}
