package app.wearly.ai.application.port.in;

import java.util.Map;

/**
 * 공급자 한 행 + 키 상태. 키 값은 어디에도 없다.
 * usable = enabled + 모델 + 키 모두 갖춤.
 */
public record AiProviderInfo(String providerId, String type, String displayName, String model, String apiKeyEnv,
        boolean keyConfigured, boolean enabled, int priority, Map<String, Object> options, boolean usable) {
}
