package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_weather_setting JPA 엔티티 (한 행, id = 1) */
@Entity
@Table(name = "tb_weather_setting")
public class WeatherSettingEntity {

  public static final short ID = 1;

  @Id
  @Column(name = "id")
  private Short id;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "interval_minutes", nullable = false)
  private int intervalMinutes;

  @Column(name = "forecast_days", nullable = false)
  private int forecastDays;

  @Column(name = "failure_threshold", nullable = false)
  private int failureThreshold;

  @Column(name = "cooldown_minutes", nullable = false)
  private int cooldownMinutes;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected WeatherSettingEntity() {}

  public WeatherSettingEntity(short id) {
    this.id = id;
  }

  public void update(
      boolean enabled,
      int intervalMinutes,
      int forecastDays,
      int failureThreshold,
      int cooldownMinutes,
      Long updatedBy,
      Instant updatedAt) {
    this.enabled = enabled;
    this.intervalMinutes = intervalMinutes;
    this.forecastDays = forecastDays;
    this.failureThreshold = failureThreshold;
    this.cooldownMinutes = cooldownMinutes;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getIntervalMinutes() {
    return intervalMinutes;
  }

  public int getForecastDays() {
    return forecastDays;
  }

  public int getFailureThreshold() {
    return failureThreshold;
  }

  public int getCooldownMinutes() {
    return cooldownMinutes;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
