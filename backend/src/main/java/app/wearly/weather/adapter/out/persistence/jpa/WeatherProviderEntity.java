package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * tb_weather_provider JPA 엔티티. 상태 칸(consecutive_failures ~ last_error)은 실행이 health()로, 설정 칸은 관리자가
 * configure()로 바꾼다. @DynamicUpdate로 바뀐 칸만 UPDATE해서 실행 중에 관리자가 설정을 저장해도 서로 덮어쓰지 않는다.
 */
@Entity
@DynamicUpdate
@Table(name = "tb_weather_provider")
public class WeatherProviderEntity {

  @Id
  @Column(name = "provider_code", length = 20)
  private String code;

  @Column(name = "display_name", nullable = false, length = 50)
  private String displayName;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "key_name", nullable = false, length = 100)
  private String keyName;

  @Column(name = "monthly_quota")
  private Integer monthlyQuota;

  @Column(name = "max_forecast_days", nullable = false)
  private int maxForecastDays;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  @Column(name = "circuit_reason")
  private String circuitReason;

  @Column(name = "circuit_open_until")
  private Instant circuitOpenUntil;

  @Column(name = "last_success_at")
  private Instant lastSuccessAt;

  @Column(name = "last_failure_at")
  private Instant lastFailureAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected WeatherProviderEntity() {}

  /** 관리자 설정 칸만 바꾼다 */
  public void configure(
      boolean enabled,
      int priority,
      Integer monthlyQuota,
      int maxForecastDays,
      Long updatedBy,
      Instant updatedAt) {
    this.enabled = enabled;
    this.priority = priority;
    this.monthlyQuota = monthlyQuota;
    this.maxForecastDays = maxForecastDays;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  /** 실행 상태 칸만 바꾼다 */
  public void health(
      int consecutiveFailures,
      String circuitReason,
      Instant circuitOpenUntil,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      String lastError) {
    this.consecutiveFailures = consecutiveFailures;
    this.circuitReason = circuitReason;
    this.circuitOpenUntil = circuitOpenUntil;
    this.lastSuccessAt = lastSuccessAt;
    this.lastFailureAt = lastFailureAt;
    this.lastError = lastError;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getPriority() {
    return priority;
  }

  public String getKeyName() {
    return keyName;
  }

  public Integer getMonthlyQuota() {
    return monthlyQuota;
  }

  public int getMaxForecastDays() {
    return maxForecastDays;
  }

  public int getConsecutiveFailures() {
    return consecutiveFailures;
  }

  public String getCircuitReason() {
    return circuitReason;
  }

  public Instant getCircuitOpenUntil() {
    return circuitOpenUntil;
  }

  public Instant getLastSuccessAt() {
    return lastSuccessAt;
  }

  public Instant getLastFailureAt() {
    return lastFailureAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
