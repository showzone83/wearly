package app.wearly.code;

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

/** 코드 한 행 (관리자 쓰기는 JPA). 읽기는 CodeCatalog 캐시를 쓴다. */
@Entity
@Table(name = "tb_code")
public class Code {

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
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", nullable = false)
    private Map<String, Object> attrs = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Code() {
    }

    public Code(String groupCode, String code) {
        this.groupCode = groupCode;
        this.code = code;
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

    /** 변경 이력(before/after)에 남기는 모양 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", groupCode);
        m.put("code", code);
        m.put("label", label);
        m.put("parent", parentCode);
        m.put("sortOrder", sortOrder);
        m.put("active", active);
        m.put("attrs", attrs == null ? Map.of() : new LinkedHashMap<>(attrs));
        return m;
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

    public void setLabel(String label) {
        this.label = label;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    /** JSONB 변경 감지를 확실히 하려고 항상 새 Map으로 바꾼다 */
    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = new LinkedHashMap<>(attrs);
    }
}
