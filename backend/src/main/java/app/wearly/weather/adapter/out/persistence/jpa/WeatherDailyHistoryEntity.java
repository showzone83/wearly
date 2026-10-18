package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.common.persistence.PersistenceClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** tb_weather_daily_history JPA 엔티티 (쓰기·한 행 읽기. 목록은 MyBatis) */
@Entity
@Table(name = "tb_weather_daily_history")
@IdClass(WeatherDailyHistoryId.class)
public class WeatherDailyHistoryEntity {

  @Id
  @Column(name = "region_code", length = 30)
  private String regionCode;

  @Id
  @Column(name = "weather_date")
  private LocalDate weatherDate;

  @Column(name = "provider_code", length = 20)
  private String providerCode;

  @Column(name = "forecast_temp")
  private Double forecastTemp;

  @Column(name = "forecast_min")
  private Double forecastMin;

  @Column(name = "forecast_max")
  private Double forecastMax;

  @Column(name = "rain_prob")
  private Integer rainProb;

  @Column(name = "cond", length = 10)
  private String cond;

  @Column(name = "cond_text", length = 100)
  private String condText;

  @Column(name = "forecast_fetched_at")
  private Instant forecastFetchedAt;

  @Column(name = "observed_min")
  private Double observedMin;

  @Column(name = "observed_max")
  private Double observedMax;

  @Column(name = "observed_temp_sum", nullable = false)
  private double observedTempSum;

  @Column(name = "observed_count", nullable = false)
  private int observedCount;

  @Column(name = "observed_rain_count", nullable = false)
  private int observedRainCount;

  @Column(name = "first_observed_at")
  private Instant firstObservedAt;

  @Column(name = "last_observed_at")
  private Instant lastObservedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WeatherDailyHistoryEntity() {}

  public WeatherDailyHistoryEntity(String regionCode, LocalDate weatherDate) {
    this.regionCode = regionCode;
    this.weatherDate = weatherDate;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = PersistenceClock.now();
  }

  /** 예보 칸 (그날 마지막으로 받은 값) */
  public void forecast(
      String providerCode,
      Double temp,
      Double min,
      Double max,
      Integer rainProb,
      String cond,
      String condText,
      Instant fetchedAt) {
    this.providerCode = providerCode;
    this.forecastTemp = temp;
    this.forecastMin = min;
    this.forecastMax = max;
    this.rainProb = rainProb;
    this.cond = cond;
    this.condText = condText;
    this.forecastFetchedAt = fetchedAt;
  }

  public void observed(
      Double min,
      Double max,
      double tempSum,
      int count,
      int rainCount,
      Instant firstObservedAt,
      Instant lastObservedAt) {
    this.observedMin = min;
    this.observedMax = max;
    this.observedTempSum = tempSum;
    this.observedCount = count;
    this.observedRainCount = rainCount;
    this.firstObservedAt = firstObservedAt;
    this.lastObservedAt = lastObservedAt;
  }

  public String getRegionCode() {
    return regionCode;
  }

  public LocalDate getWeatherDate() {
    return weatherDate;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public Double getForecastTemp() {
    return forecastTemp;
  }

  public Double getForecastMin() {
    return forecastMin;
  }

  public Double getForecastMax() {
    return forecastMax;
  }

  public Integer getRainProb() {
    return rainProb;
  }

  public String getCond() {
    return cond;
  }

  public String getCondText() {
    return condText;
  }

  public Instant getForecastFetchedAt() {
    return forecastFetchedAt;
  }

  public Double getObservedMin() {
    return observedMin;
  }

  public Double getObservedMax() {
    return observedMax;
  }

  public double getObservedTempSum() {
    return observedTempSum;
  }

  public int getObservedCount() {
    return observedCount;
  }

  public int getObservedRainCount() {
    return observedRainCount;
  }

  public Instant getFirstObservedAt() {
    return firstObservedAt;
  }

  public Instant getLastObservedAt() {
    return lastObservedAt;
  }
}
