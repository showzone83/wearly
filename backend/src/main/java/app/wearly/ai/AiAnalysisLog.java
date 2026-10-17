package app.wearly.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** AI 호출 기록. 이미지는 남기지 않고 해시·크기·결과 JSON·메타만 둔다. */
@Entity
@Table(name = "tb_ai_analysis_log")
public class AiAnalysisLog {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_analysis_log_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "feature", nullable = false, length = 30)
    private String feature = "ITEM_ANALYZE";

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "image_sha256", length = 64)
    private String imageSha256;

    @Column(name = "image_bytes")
    private Integer imageBytes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private Map<String, Object> result;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiAnalysisLog() {
    }

    private AiAnalysisLog(Long userId, String provider, String model, String status, int latencyMs, AiImage image) {
        this.userId = userId;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.latencyMs = latencyMs;
        this.imageSha256 = image.sha256();
        this.imageBytes = image.bytes().length;
    }

    public static AiAnalysisLog success(Long userId, String provider, AiResult result, Map<String, Object> resultJson,
            int latencyMs, AiImage image) {
        AiAnalysisLog log = new AiAnalysisLog(userId, provider, result.model(), SUCCESS, latencyMs, image);
        log.inputTokens = toInt(result.inputTokens());
        log.outputTokens = toInt(result.outputTokens());
        log.result = resultJson;
        return log;
    }

    public static AiAnalysisLog failure(Long userId, String provider, String model, String errorCode, int latencyMs,
            AiImage image) {
        AiAnalysisLog log = new AiAnalysisLog(userId, provider, model, FAILED, latencyMs, image);
        log.errorCode = errorCode.length() > 50 ? errorCode.substring(0, 50) : errorCode;
        return log;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static Integer toInt(Long value) {
        return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }
}
