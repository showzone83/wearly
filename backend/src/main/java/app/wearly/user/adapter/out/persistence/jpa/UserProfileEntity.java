package app.wearly.user.adapter.out.persistence.jpa;

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

/** tb_user_profile JPA 엔티티. 배열은 TEXT[]/SMALLINT[], 알림은 JSONB */
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
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
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
      boolean onboarded) {
    this.dressCodes = dressCodes;
    this.moods = moods;
    this.colors = colors;
    this.workType = workType;
    this.workDays = workDays;
    this.workRule = workRule;
    this.offRule = offRule;
    this.notify = notify;
    this.onboarded = onboarded;
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
}
