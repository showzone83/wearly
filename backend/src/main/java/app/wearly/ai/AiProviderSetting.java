package app.wearly.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** AI 공급자 설정 한 행 (tb_ai_provider). 키 값은 없고 키가 든 환경변수 이름만 둔다 */
@Entity
@Table(name = "tb_ai_provider")
public class AiProviderSetting {

    @Id
    @Column(name = "provider_id", length = 30)
    private String providerId;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "api_key_env", nullable = false, length = 100)
    private String apiKeyEnv;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false)
    private Map<String, Object> options;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiProviderSetting() {
    }

    public AiProviderSetting(String providerId) {
        this.providerId = providerId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(AiProviderType type, String displayName, String model, String apiKeyEnv, boolean enabled,
            int priority, Map<String, Object> options) {
        this.type = type.name();
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

    public String getProviderId() {
        return providerId;
    }

    public AiProviderType getType() {
        return AiProviderType.valueOf(type);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModel() {
        return model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public Map<String, Object> getOptions() {
        return options == null ? Map.of() : options;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
