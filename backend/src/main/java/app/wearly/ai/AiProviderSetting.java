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

/** AI 공급자 설정 한 행 (tb_ai_provider). 키 값은 없고 키가 든 환경변수 이름만 둔다. */
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
    private Map<String, Object> options = new LinkedHashMap<>();

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

    public AiProviderType typeEnum() {
        return AiProviderType.valueOf(type);
    }

    public boolean hasModel() {
        return model != null && !model.isBlank();
    }

    /** 변경 이력(before/after)에 남기는 모양. 키 값은 애초에 없다 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("providerId", providerId);
        m.put("type", type);
        m.put("displayName", displayName);
        m.put("model", model);
        m.put("apiKeyEnv", apiKeyEnv);
        m.put("enabled", enabled);
        m.put("priority", priority);
        m.put("options", options == null ? Map.of() : new LinkedHashMap<>(options));
        return m;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = new LinkedHashMap<>(options);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
