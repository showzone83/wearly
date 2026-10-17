package app.wearly.ai.adapter.out.ai;

import app.wearly.ai.application.port.out.AiModelCatalogPort;
import app.wearly.ai.domain.AiModelEntry;
import app.wearly.ai.domain.AiProviderSetting;
import app.wearly.common.config.WearlyProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 제공사 모델 목록 조회 (관리자 화면 모델 드롭다운). 종류별 호출은 AiProviderModule.listModels (Claude SDK models().list(),
 * OpenAI GET /models, Gemini GET /v1beta/models). 공급자 options의
 * timeoutSeconds·baseUrl(WEARLY_AI_ALLOW_BASE_URL=true일 때만)을 따른다.
 */
@Component
class AiModelCatalogAdapter implements AiModelCatalogPort {

  private final WearlyProperties.Ai props;
  private final AiProviderModules modules;

  AiModelCatalogAdapter(WearlyProperties properties, AiProviderModules modules) {
    this.props = properties.ai();
    this.modules = modules;
  }

  @Override
  public List<AiModelEntry> listModels(AiProviderSetting s, String apiKey) {
    return modules.of(s.type()).listModels(apiKey, ProviderOptions.of(s, props));
  }
}
