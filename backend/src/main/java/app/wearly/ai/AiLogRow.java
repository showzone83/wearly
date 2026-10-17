package app.wearly.ai;

import java.time.Instant;
import java.util.Map;

/** MyBatis로 읽은 AI 호출 기록 (result는 JSONB → Map) */
public class AiLogRow {

    private Long id;
    private String feature;
    private String provider;
    private String model;
    private String status;
    private String imageSha256;
    private Map<String, Object> result;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageSha256() {
        return imageSha256;
    }

    public void setImageSha256(String imageSha256) {
        this.imageSha256 = imageSha256;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
