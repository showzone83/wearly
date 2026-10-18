package app.wearly.user.adapter.out.persistence.jpa;

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
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_user_profile JPA 엔티티. 배열은 TEXT[]/SMALLINT[], 알림은 JSONB. 나의 정보 4칸은 NULL이면 비어 있다 (V21) */
@Entity
@Table(name = "tb_user_profile")
public class UserProfileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "profile_id")
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "dress_codes", nullable = false)
  private List<String> dressCodes;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "moods", nullable = false)
  private List<String> moods;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "colors", nullable = false)
  private List<String> colors;

  @Column(name = "work_type", nullable = false, length = 10)
  private String workType;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "work_days", nullable = false)
  private List<Short> workDays;

  @Column(name = "work_rule", nullable = false)
  private boolean workRule;

  @Column(name = "off_rule", nullable = false)
  private boolean offRule;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "notify", nullable = false)
  private Map<String, Boolean> notify;

  @Column(name = "onboarded", nullable = false)
  private boolean onboarded;

  @Column(name = "age_range", length = 30)
  private String ageRange;

  @Column(name = "body_type", length = 30)
  private String bodyType;

  @Column(name = "height_cm")
  private Short heightCm;

  @Column(name = "fit_preference", length = 30)
  private String fitPreference;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserProfileEntity() {}

  public UserProfileEntity(Long userId) {
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

  public void update(
      List<String> dressCodes,
      List<String> moods,
      List<String> colors,
      String workType,
      List<Short> workDays,
      boolean workRule,
      boolean offRule,
      Map<String, Boolean> notify,
      boolean onboarded,
      String ageRange,
      String bodyType,
      Short heightCm,
      String fitPreference) {
    this.dressCodes = dressCodes;
    this.moods = moods;
    this.colors = colors;
    this.workType = workType;
    this.workDays = workDays;
    this.workRule = workRule;
    this.offRule = offRule;
    this.notify = notify;
    this.onboarded = onboarded;
    this.ageRange = ageRange;
    this.bodyType = bodyType;
    this.heightCm = heightCm;
    this.fitPreference = fitPreference;
  }

  public List<String> getDressCodes() {
    return dressCodes;
  }

  public List<String> getMoods() {
    return moods;
  }

  public List<String> getColors() {
    return colors;
  }

  public String getWorkType() {
    return workType;
  }

  public List<Short> getWorkDays() {
    return workDays;
  }

  public boolean isWorkRule() {
    return workRule;
  }

  public boolean isOffRule() {
    return offRule;
  }

  public Map<String, Boolean> getNotify() {
    return notify;
  }

  public boolean isOnboarded() {
    return onboarded;
  }

  public String getAgeRange() {
    return ageRange;
  }

  public String getBodyType() {
    return bodyType;
  }

  public Short getHeightCm() {
    return heightCm;
  }

  public String getFitPreference() {
    return fitPreference;
  }
}
