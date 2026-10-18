package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** tb_weather_climate JPA 엔티티 (격자 + 달) */
@Entity
@Table(name = "tb_weather_climate")
@IdClass(WeatherClimateEntity.Key.class)
public class WeatherClimateEntity {

  /** 복합 키 (grid_key, month) */
  public static class Key implements Serializable {

    private String gridKey;
    private short month;

    public Key() {}

    public Key(String gridKey, short month) {
      this.gridKey = gridKey;
      this.month = month;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && k.month == month && Objects.equals(k.gridKey, gridKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(gridKey, month);
    }
  }

  @Id
  @Column(name = "grid_key", length = 30)
  private String gridKey;

  @Id
  @Column(name = "month")
  private short month;

  @Column(name = "temp_avg", nullable = false)
  private double tempAvg;

  @Column(name = "temp_min", nullable = false)
  private double tempMin;

  @Column(name = "temp_max", nullable = false)
  private double tempMax;

  @Column(name = "rain_day_ratio", nullable = false)
  private double rainDayRatio;

  @Column(name = "years", nullable = false, length = 20)
  private String years;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "computed_at", nullable = false)
  private Instant computedAt;

  protected WeatherClimateEntity() {}

  public WeatherClimateEntity(String gridKey, short month) {
    this.gridKey = gridKey;
    this.month = month;
  }

  public void fill(
      double tempAvg,
      double tempMin,
      double tempMax,
      double rainDayRatio,
      String years,
      String source,
      Instant computedAt) {
    this.tempAvg = tempAvg;
    this.tempMin = tempMin;
    this.tempMax = tempMax;
    this.rainDayRatio = rainDayRatio;
    this.years = years;
    this.source = source;
    this.computedAt = computedAt;
  }

  public String getGridKey() {
    return gridKey;
  }

  public short getMonth() {
    return month;
  }

  public double getTempAvg() {
    return tempAvg;
  }

  public double getTempMin() {
    return tempMin;
  }

  public double getTempMax() {
    return tempMax;
  }

  public double getRainDayRatio() {
    return rainDayRatio;
  }

  public String getYears() {
    return years;
  }

  public String getSource() {
    return source;
  }

  public Instant getComputedAt() {
    return computedAt;
  }
}
