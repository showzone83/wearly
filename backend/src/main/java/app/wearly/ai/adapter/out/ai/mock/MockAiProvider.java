package app.wearly.ai.adapter.out.ai.mock;

import app.wearly.ai.application.port.out.AiModelPort;
import app.wearly.ai.domain.AiImage;
import app.wearly.ai.domain.AiJsonPrompt;
import app.wearly.ai.domain.AiJsonResult;
import app.wearly.ai.domain.AiProviderException;
import app.wearly.ai.domain.AiProviderRules;

/**
 * 목업 공급자 (WEARLY_AI_PROVIDER=mock일 때만). 호출한 쪽이 요청에 넣은 결정적 응답(mockOutput)을 그대로 돌려준다. 사진 분석은 도메인
 * MockSuggestions가 사진 해시로, 코디 추천은 MockOutfitPlanner가 옷장으로 만든다. 실제 공급자 응답과 같은 검증을 거친다.
 */
public class MockAiProvider implements AiModelPort {

  public static final String ID = AiProviderRules.MOCK_ID;

  @Override
  public String name() {
    return ID;
  }

  @Override
  public String model() {
    return null;
  }

  @Override
  public AiJsonResult generateJson(AiJsonPrompt prompt, AiImage image) {
    if (prompt.mockOutput() == null) {
      throw new AiProviderException("NO_MOCK_OUTPUT", "목업 응답이 없는 요청이에요.");
    }
    return new AiJsonResult(prompt.mockOutput().get(), null, null, null);
  }
}
