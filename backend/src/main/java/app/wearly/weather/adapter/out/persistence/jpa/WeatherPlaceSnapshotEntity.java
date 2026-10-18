package app.wearly.weather.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_weather_place_snapshot JPA 엔티티. current·days는 JSONB (tb_weather_snapshot과 같은 모양) */
@Entity
@Table(name = "tb_weather_place_snapshot")
public class WeatherPlaceSnapshotEntity {

  @Id
  @Column(name = "grid_key", length = 30)
  private String gridKey;

  @Column(name = "lat", nullable = false)
  private double lat;

  @Column(name = "lon", nullable = false)
  private double lon;

  @Column(name = "label", length = 100)
  private String label;

  @Column(name = "provider_code", length = 20)
  private String providerCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "current")
  private Map<String, Object> current;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "days")
  private List<Map<String, Object>> days;

  @Column(name = "fetched_at")
  private Instant fetchedAt;

  @Column(name = "last_error", length = 300)
  private String lastError;

  @Column(name = "last_error_at")
  private Instant lastErrorAt;

  protected WeatherPlaceSnapshotEntity() {}

  public WeatherPlaceSnapshotEntity(String gridKey, double lat, double lon) {
    this.gridKey = gridKey;
    this.lat = lat;
    this.lon = lon;
  }

  /** 받은 값으로 바꾸고 이전 오류를 비운다 */
  public void fill(
      String label,
      String providerCode,
      Map<String, Object> current,
      List<Map<String, Object>> days,
      Instant fetchedAt) {
    this.label = label;
    this.providerCode = providerCode;
    this.current = current;
    this.days = days;
    this.fetchedAt = fetchedAt;
    this.lastError = null;
    this.lastErrorAt = null;
  }

  public void error(String lastError, Instant lastErrorAt) {
    this.lastError = lastError;
    this.lastErrorAt = lastErrorAt;
  }

  public String getGridKey() {
    return gridKey;
  }

  public double getLat() {
    return lat;
  }

  public double getLon() {
    return lon;
  }

  public String getLabel() {
    return label;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public Map<String, Object> getCurrent() {
    return current;
  }

  public List<Map<String, Object>> getDays() {
    return days;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getLastErrorAt() {
    return lastErrorAt;
  }
}
