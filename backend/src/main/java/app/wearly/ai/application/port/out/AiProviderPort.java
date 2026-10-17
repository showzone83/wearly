package app.wearly.ai.application.port.out;

import app.wearly.ai.domain.AiProviderSetting;
import java.util.List;
import java.util.Optional;

/** AI 공급자 설정 저장·로드 (JPA, tb_ai_provider) */
public interface AiProviderPort {

  /** priority, provider_id 순서 */
  List<AiProviderSetting> findAllOrdered();

  Optional<AiProviderSetting> findById(String providerId);

  boolean exists(String providerId);

  int maxPriority();

  AiProviderSetting create(AiProviderSetting setting);

  AiProviderSetting update(AiProviderSetting setting);

  void delete(String providerId);
}
