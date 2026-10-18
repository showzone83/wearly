package app.wearly.wardrobe.adapter.out.persistence.jpa;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * tb_wardrobe_item JPA 엔티티 (쓰기·단건 조회). seasons·styles·sub_colors는 TEXT[], 사용 중지는
 * retired_at·retire_reason·retire_memo, 구입일은 purchased_at·purchased_at_estimated (이슈 #4)
 */
@Entity
@Table(name = "tb_wardrobe_item")
public class WardrobeItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "name", nullable = false, length = 40)
  private String name;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "sub_type", nullable = false, length = 30)
  private String subType;

  @Column(name = "color", nullable = false, length = 30)
  private String color;

  /** 보조 색 0~2개 (이슈 #4) */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "sub_colors", nullable = false)
  private List<String> subColors;

  @Column(name = "pattern", nullable = false, length = 30)
  private String pattern;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "seasons", nullable = false)
  private List<String> seasons;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "styles", nullable = false)
  private List<String> styles;

  @Column(name = "formality", nullable = false)
  private short formality;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "brand", length = 40)
  private String brand;

  @Column(name = "worn_count", nullable = false)
  private int wornCount;

  @Column(name = "last_worn_at")
  private LocalDate lastWornAt;

  @Column(name = "purchased_at", nullable = false)
  private LocalDate purchasedAt;

  @Column(name = "purchased_at_estimated", nullable = false)
  private boolean purchasedAtEstimated;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @Column(name = "retire_reason", length = 30)
  private String retireReason;

  @Column(name = "retire_memo", length = 200)
  private String retireMemo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WardrobeItemEntity() {}

  public WardrobeItemEntity(Long userId) {
    this.userId = userId;
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

  /** 모든 값을 덮어쓴다 (등록과 PATCH 병합 결과 저장에 같이 쓴다). */
  public void apply(
      String name,
      String category,
      String subType,
      String color,
      List<String> subColors,
      String pattern,
      List<String> seasons,
      List<String> styles,
      short formality,
      String status,
      String brand,
      int wornCount,
      LocalDate lastWornAt,
      LocalDate purchasedAt,
      boolean purchasedAtEstimated) {
    this.name = name;
    this.category = category;
    this.subType = subType;
    this.color = color;
    this.subColors = subColors;
    this.pattern = pattern;
    this.seasons = seasons;
    this.styles = styles;
    this.formality = formality;
    this.status = status;
    this.brand = brand;
    this.wornCount = wornCount;
    this.lastWornAt = lastWornAt;
    this.purchasedAt = purchasedAt;
    this.purchasedAtEstimated = purchasedAtEstimated;
  }

  /** 사용 중지 상태 (13 문서). 사용 중이면 세 값 모두 null */
  public void retirement(Instant retiredAt, String retireReason, String retireMemo) {
    this.retiredAt = retiredAt;
    this.retireReason = retireReason;
    this.retireMemo = retireMemo;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }

  public String getRetireReason() {
    return retireReason;
  }

  public String getRetireMemo() {
    return retireMemo;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }

  public String getSubType() {
    return subType;
  }

  public String getColor() {
    return color;
  }

  public List<String> getSubColors() {
    return subColors;
  }

  public String getPattern() {
    return pattern;
  }

  public List<String> getSeasons() {
    return seasons;
  }

  public List<String> getStyles() {
    return styles;
  }

  public short getFormality() {
    return formality;
  }

  public String getStatus() {
    return status;
  }

  public String getBrand() {
    return brand;
  }

  public int getWornCount() {
    return wornCount;
  }

  public LocalDate getLastWornAt() {
    return lastWornAt;
  }

  public LocalDate getPurchasedAt() {
    return purchasedAt;
  }

  public boolean isPurchasedAtEstimated() {
    return purchasedAtEstimated;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
