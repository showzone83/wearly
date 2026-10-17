package app.wearly.ai.application.port.in;

import java.util.Map;

/** 공급자 추가 (07 문서 4장). enabled가 없으면 false, priority가 없으면 맨 뒤 */
public record CreateProviderCommand(
    String providerId,
    String type,
    String displayName,
    String model,
    String apiKeyEnv,
    Boolean enabled,
    Integer priority,
    Map<String, Object> options) {}
