package app.wearly.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 호출 기록. 이미지는 남기지 않고 해시·크기·결과 JSON·메타만 둔다.
 * 호출 전에 PENDING으로 자리를 잡고(한도 동시성 보호), 끝나면 SUCCESS 또는 FAILED로 바꾼다.
 */
@Entity
@Table(name = "tb_ai_analysis_log")
public class AiAnalysisLog {

    public static final String PENDING = "PENDING";
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

    @Column(name = "latency_ms")
    private Integer latencyMs;

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

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AiAnalysisLog() {
    }

    /** 공급자를 부르기 전에 남기는 기록. 진행 중인 호출도 한도에 센다 (AiUsageMapper.countUsed). */
    public static AiAnalysisLog pending(Long userId, String provider, String model, AiImage image, Instant now) {
        AiAnalysisLog log = new AiAnalysisLog();
        log.userId = userId;
        log.provider = provider;
        log.model = model;
        log.status = PENDING;
        log.imageSha256 = image.sha256();
        log.imageBytes = image.bytes().length;
        log.createdAt = now;
        return log;
    }

    public void succeed(AiResult result, Map<String, Object> resultJson, int latencyMs, Instant now) {
        this.status = SUCCESS;
        if (result.model() != null) {
            this.model = result.model(); // 폴백으로 다른 모델이 답했으면 실제 모델을 남긴다
        }
        this.inputTokens = toInt(result.inputTokens());
        this.outputTokens = toInt(result.outputTokens());
        this.result = resultJson;
        this.latencyMs = latencyMs;
        this.completedAt = now;
    }

    public void fail(String errorCode, int latencyMs, Instant now) {
        this.status = FAILED;
        this.errorCode = errorCode.length() > 50 ? errorCode.substring(0, 50) : errorCode;
        this.latencyMs = latencyMs;
        this.completedAt = now;
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
