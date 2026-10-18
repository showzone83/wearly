package app.wearly.plan.adapter.out.persistence.jpa;

import app.wearly.common.persistence.PersistenceClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_planned_outfit JPA 엔티티 (V14). concepts는 TEXT[], weather·outfit은 JSONB, 장소는 place_* 네 칸 */
@Entity
@Table(name = "tb_planned_outfit")
public class PlanEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "planned_outfit_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "client_id", length = 64, updatable = false)
  private String clientId;

  @Column(name = "planned_date", nullable = false)
  private LocalDate plannedDate;

  @Column(name = "time_of_day", nullable = false, length = 10)
  private String timeOfDay;

  @Column(name = "title", nullable = false, length = 50)
  private String title;

  @Column(name = "occasion", nullable = false, length = 20)
  private String occasion;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "concepts", nullable = false)
  private List<String> concepts;

  @Column(name = "memo", length = 500)
  private String memo;

  @Column(name = "place_region_code", length = 30)
  private String placeRegionCode;

  @Column(name = "place_label", length = 50)
  private String placeLabel;

  @Column(name = "place_lat")
  private Double placeLat;

  @Column(name = "place_lon")
  private Double placeLon;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "weather", nullable = false)
  private Map<String, Object> weather;

  @Column(name = "forecast_ready", nullable = false)
  private boolean forecastReady;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "outfit")
  private Map<String, Object> outfit;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlanEntity() {}

  public PlanEntity(Long userId, String clientId) {
    this.userId = userId;
    this.clientId = clientId;
  }

  @PrePersist
  void onCreate() {
    createdAt = PersistenceClock.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = PersistenceClock.now();
  }

  /** 모든 값을 덮어쓴다 (등록·PATCH 병합 결과·같은 clientId 갱신) */
  public void apply(
      LocalDate plannedDate,
      String timeOfDay,
      String title,
      String occasion,
      List<String> concepts,
      String memo,
      String placeRegionCode,
      String placeLabel,
      Double placeLat,
      Double placeLon,
      Map<String, Object> weather,
      boolean forecastReady,
      Map<String, Object> outfit) {
    this.plannedDate = plannedDate;
    this.timeOfDay = timeOfDay;
    this.title = title;
    this.occasion = occasion;
    this.concepts = concepts;
    this.memo = memo;
    this.placeRegionCode = placeRegionCode;
    this.placeLabel = placeLabel;
    this.placeLat = placeLat;
    this.placeLon = placeLon;
    this.weather = weather;
    this.forecastReady = forecastReady;
    this.outfit = outfit;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getClientId() {
    return clientId;
  }

  public LocalDate getPlannedDate() {
    return plannedDate;
  }

  public String getTimeOfDay() {
    return timeOfDay;
  }

  public String getTitle() {
    return title;
  }

  public String getOccasion() {
    return occasion;
  }

  public List<String> getConcepts() {
    return concepts;
  }

  public String getMemo() {
    return memo;
  }

  public String getPlaceRegionCode() {
    return placeRegionCode;
  }

  public String getPlaceLabel() {
    return placeLabel;
  }

  public Double getPlaceLat() {
    return placeLat;
  }

  public Double getPlaceLon() {
    return placeLon;
  }

  public Map<String, Object> getWeather() {
    return weather;
  }

  public boolean isForecastReady() {
    return forecastReady;
  }

  public Map<String, Object> getOutfit() {
    return outfit;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
