package app.wearly.admin.adapter.out.persistence.jpa;

import app.wearly.common.persistence.PersistenceClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_admin_audit_log JPA 엔티티 (before/after는 바뀌기 전후 모습) */
@Entity
@Table(name = "tb_admin_audit_log")
public class AuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_id")
  private Long id;

  @Column(name = "admin_user_id")
  private Long adminUserId;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Column(name = "target_type", nullable = false, length = 30)
  private String targetType;

  @Column(name = "target_id", nullable = false, length = 100)
  private String targetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before")
  private Map<String, Object> before;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after")
  private Map<String, Object> after;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AuditLogEntity() {}

  public AuditLogEntity(
      Long adminUserId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after) {
    this.adminUserId = adminUserId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.before = before;
    this.after = after;
  }

  @PrePersist
  void onCreate() {
    createdAt = PersistenceClock.now();
  }
}
