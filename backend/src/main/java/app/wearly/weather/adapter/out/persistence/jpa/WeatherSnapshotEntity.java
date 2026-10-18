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

/** tb_weather_snapshot JPA 엔티티. current·days·raw는 JSONB (모양은 WeatherSnapshotJpaAdapter가 정한다) */
@Entity
@Table(name = "tb_weather_snapshot")
public class WeatherSnapshotEntity {

  @Id
  @Column(name = "region_code", length = 30)
  private String regionCode;

  @Column(name = "provider_code", length = 20)
  private String providerCode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "current")
  private Map<String, Object> current;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "days")
  private List<Map<String, Object>> days;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw")
  private Map<String, Object> raw;

  @Column(name = "location_name", length = 100)
  private String locationName;

  @Column(name = "fetched_at")
  private Instant fetchedAt;

  @Column(name = "last_error", length = 300)
  private String lastError;

  @Column(name = "last_error_at")
  private Instant lastErrorAt;

  protected WeatherSnapshotEntity() {}

  public WeatherSnapshotEntity(String regionCode) {
    this.regionCode = regionCode;
  }

  /** 받은 값으로 바꾸고 이전 오류를 비운다 */
  public void fill(
      String providerCode,
      Map<String, Object> current,
      List<Map<String, Object>> days,
      Map<String, Object> raw,
      String locationName,
      Instant fetchedAt) {
    this.providerCode = providerCode;
    this.current = current;
    this.days = days;
    this.raw = raw;
    this.locationName = locationName;
    this.fetchedAt = fetchedAt;
    this.lastError = null;
    this.lastErrorAt = null;
  }

  public void error(String lastError, Instant lastErrorAt) {
    this.lastError = lastError;
    this.lastErrorAt = lastErrorAt;
  }

  public String getRegionCode() {
    return regionCode;
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

  public Map<String, Object> getRaw() {
    return raw;
  }

  public String getLocationName() {
    return locationName;
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
