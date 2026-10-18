package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_weather_refresh_run JPA 엔티티 */
@Entity
@Table(name = "tb_weather_refresh_run")
public class WeatherRefreshRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "run_id")
  private Long runId;

  @Column(name = "trigger_type", nullable = false, length = 20, updatable = false)
  private String trigger;

  @Column(name = "requested_by", updatable = false)
  private Long requestedBy;

  @Column(name = "region_code", length = 30, updatable = false)
  private String regionCode;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "skip_reason", length = 20)
  private String skipReason;

  @Column(name = "success_count", nullable = false)
  private int successCount;

  @Column(name = "fail_count", nullable = false)
  private int failCount;

  @Column(name = "fallback_count", nullable = false)
  private int fallbackCount;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  protected WeatherRefreshRunEntity() {}

  public WeatherRefreshRunEntity(
      String trigger, Long requestedBy, String regionCode, Instant startedAt) {
    this.trigger = trigger;
    this.requestedBy = requestedBy;
    this.regionCode = regionCode;
    this.startedAt = startedAt;
  }

  public void result(
      String status,
      String skipReason,
      int successCount,
      int failCount,
      int fallbackCount,
      Instant finishedAt) {
    this.status = status;
    this.skipReason = skipReason;
    this.successCount = successCount;
    this.failCount = failCount;
    this.fallbackCount = fallbackCount;
    this.finishedAt = finishedAt;
  }

  public Long getRunId() {
    return runId;
  }

  public String getTrigger() {
    return trigger;
  }

  public Long getRequestedBy() {
    return requestedBy;
  }

  public String getRegionCode() {
    return regionCode;
  }

  public String getStatus() {
    return status;
  }

  public String getSkipReason() {
    return skipReason;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getFailCount() {
    return failCount;
  }

  public int getFallbackCount() {
    return fallbackCount;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }
}
