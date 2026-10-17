package app.wearly.admin;

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

/** 관리자 변경 이력 한 건 (tb_admin_audit_log). before/after는 바뀐 대상의 전체 모양 */
@Entity
@Table(name = "tb_admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "action", nullable = false, length = 40)
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

    protected AdminAuditLog() {
    }

    public AdminAuditLog(Long adminUserId, String action, String targetType, String targetId, Map<String, Object> before,
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
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }
}
