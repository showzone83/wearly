package app.wearly.admin.adapter.in.web.request;

import app.wearly.ai.application.port.in.dto.CreateProviderDto;
import java.util.Map;

/**
 * POST /admin/ai/providers 본문 (07 문서 4장). 관리자 API의 JSON 모양은 여기서 정하고 ai 컨텍스트의 입력 DTO로 바꾼다. enabled가
 * 없으면 false, priority가 없으면 맨 뒤. 검증은 유스케이스가 한다
 */
public record CreateProviderRequest(
    String providerId,
    String type,
    String displayName,
    String model,
    String apiKeyEnv,
    Boolean enabled,
    Integer priority,
    Map<String, Object> options) {

  public CreateProviderDto toDto() {
    return new CreateProviderDto(
        providerId, type, displayName, model, apiKeyEnv, enabled, priority, options);
  }
}
