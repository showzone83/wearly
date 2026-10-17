package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.application.port.out.ApiKeyVerifierPort;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.common.config.WearlyProperties;
import org.springframework.stereotype.Component;

/**
 * 웹에서 넣은 키를 저장하기 전에 각 사의 모델 목록 조회로 확인한다 (07 문서 6장). 종류별 호출은 AiProviderModule.verify (Claude SDK
 * models().list(), OpenAI GET /models, Gemini GET /v1beta/models?pageSize=1). 공급자 options의
 * timeoutSeconds·baseUrl(WEARLY_AI_ALLOW_BASE_URL=true일 때만)을 따른다. 오류 메시지에서 키를 지운다.
 */
@Component
class ApiKeyVerifier implements ApiKeyVerifierPort {

  private final WearlyProperties.Ai props;
  private final AiProviderModules modules;

  ApiKeyVerifier(WearlyProperties properties, AiProviderModules modules) {
    this.props = properties.ai();
    this.modules = modules;
  }

  @Override
  public void verify(AiProviderSetting s, String apiKey) {
    modules.of(s.type()).verify(apiKey, ProviderOptions.of(s, props));
  }
}
