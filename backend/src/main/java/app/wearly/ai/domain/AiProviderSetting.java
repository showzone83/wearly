package app.wearly.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 공급자 설정 한 행 (순수 도메인 모델, 07 문서 3장). 키 값은 없고 키가 든 환경변수 이름만 둔다.
 * updatedAt은 공급자 클라이언트를 다시 만들지 정할 때 쓴다.
 */
public class AiProviderSetting {

    private final String providerId;
    private AiProviderType type;
    private String displayName;
    private String model;
    private String apiKeyEnv;
    private boolean enabled;
    private int priority;
    private Map<String, Object> options;
    private final Instant updatedAt;

    public AiProviderSetting(String providerId, AiProviderType type, String displayName, String model, String apiKeyEnv,
            boolean enabled, int priority, Map<String, Object> options, Instant updatedAt) {
        this.providerId = providerId;
        this.type = type;
        this.displayName = displayName;
        this.model = model;
        this.apiKeyEnv = apiKeyEnv;
        this.enabled = enabled;
        this.priority = priority;
        this.options = options == null ? Map.of() : options;
        this.updatedAt = updatedAt;
    }

    public void update(AiProviderType type, String displayName, String model, String apiKeyEnv, boolean enabled,
            int priority, Map<String, Object> options) {
        this.type = type;
        this.displayName = displayName;
        this.model = model;
        this.apiKeyEnv = apiKeyEnv;
        this.enabled = enabled;
        this.priority = priority;
        this.options = new LinkedHashMap<>(options);
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }

    public String providerId() {
        return providerId;
    }

    public AiProviderType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public String model() {
        return model;
    }

    public String apiKeyEnv() {
        return apiKeyEnv;
    }

    public boolean enabled() {
        return enabled;
    }

    public int priority() {
        return priority;
    }

    public Map<String, Object> options() {
        return options;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
