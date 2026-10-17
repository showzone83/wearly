package app.wearly.ai.adapter.out.persistence.jpa;

import app.wearly.common.persistence.PersistenceClock;
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

/** tb_ai_analysis_log JPA 엔티티. 이미지는 남기지 않고 해시·크기·결과 JSON·메타만 둔다. */
@Entity
@Table(name = "tb_ai_analysis_log")
public class AiAnalysisLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ai_analysis_log_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "feature", nullable = false, length = 30)
  private String feature;

  @Column(name = "provider", nullable = false, length = 30)
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

  @Column(name = "result_summary")
  private String resultSummary;

  @Column(name = "error_code", length = 50)
  private String errorCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AiAnalysisLogEntity() {}

  public AiAnalysisLogEntity(
      Long userId,
      String feature,
      String provider,
      String model,
      String status,
      int latencyMs,
      Integer inputTokens,
      Integer outputTokens,
      String imageSha256,
      Integer imageBytes,
      Map<String, Object> result,
      String resultSummary,
      String errorCode) {
    this.userId = userId;
    this.feature = feature;
    this.provider = provider;
    this.model = model;
    this.status = status;
    this.latencyMs = latencyMs;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.imageSha256 = imageSha256;
    this.imageBytes = imageBytes;
    this.result = result;
    this.resultSummary = resultSummary;
    this.errorCode = errorCode;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = PersistenceClock.now();
    }
  }

  public Long getId() {
    return id;
  }
}
