package app.wearly.code.adapter.out.persistence.jpa;

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
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_code JPA 엔티티 (관리자 쓰기). 도메인 모델 Code와는 CodeJpaAdapter가 바꾼다 */
@Entity
@Table(name = "tb_code")
public class CodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "code_id")
  private Long id;

  @Column(name = "group_code", nullable = false, updatable = false, length = 30)
  private String groupCode;

  @Column(name = "code", nullable = false, updatable = false, length = 30)
  private String code;

  @Column(name = "label", nullable = false, length = 50)
  private String label;

  @Column(name = "parent_code", length = 30)
  private String parentCode;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "active", nullable = false)
  private boolean active;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false)
  private Map<String, Object> attrs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CodeEntity() {}

  public CodeEntity(String groupCode, String code) {
    this.groupCode = groupCode;
    this.code = code;
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

  /** 버전 문자열이 확실히 바뀌도록, 내용이 같아도 updated_at을 올린다 (순서 바꾸기·pairs 반영) */
  public void touch() {
    updatedAt = PersistenceClock.now();
  }

  public void update(
      String label, String parentCode, int sortOrder, boolean active, Map<String, Object> attrs) {
    this.label = label;
    this.parentCode = parentCode;
    this.sortOrder = sortOrder;
    this.active = active;
    this.attrs = new LinkedHashMap<>(attrs);
  }

  public Long getId() {
    return id;
  }

  public String getGroupCode() {
    return groupCode;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public String getParentCode() {
    return parentCode;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public Map<String, Object> getAttrs() {
    return attrs;
  }
}
