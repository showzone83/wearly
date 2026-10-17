package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_weather_call_log JPA 엔티티 (쓰기만, 읽기는 MyBatis) */
@Entity
@Table(name = "tb_weather_call_log")
public class WeatherCallLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "call_id")
  private Long callId;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "region_code", nullable = false, length = 30)
  private String regionCode;

  @Column(name = "provider_code", nullable = false, length = 20)
  private String providerCode;

  @Column(name = "call_kind", nullable = false, length = 20)
  private String callKind;

  @Column(name = "fallback", nullable = false)
  private boolean fallback;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_code", length = 20)
  private String errorCode;

  @Column(name = "error_message", length = 300)
  private String errorMessage;

  @Column(name = "duration_ms", nullable = false)
  private int durationMs;

  @Column(name = "response_bytes")
  private Integer responseBytes;

  @Column(name = "called_at", nullable = false)
  private Instant calledAt;

  protected WeatherCallLogEntity() {}

  public WeatherCallLogEntity(
      long runId,
      String regionCode,
      String providerCode,
      String callKind,
      boolean fallback,
      Integer httpStatus,
      boolean success,
      String errorCode,
      String errorMessage,
      int durationMs,
      Integer responseBytes,
      Instant calledAt) {
    this.runId = runId;
    this.regionCode = regionCode;
    this.providerCode = providerCode;
    this.callKind = callKind;
    this.fallback = fallback;
    this.httpStatus = httpStatus;
    this.success = success;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.durationMs = durationMs;
    this.responseBytes = responseBytes;
    this.calledAt = calledAt;
  }

  public Long getCallId() {
    return callId;
  }
}
