package app.wearly.appsetting.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_app_setting JPA 엔티티. 키 하나에 JSON 값 하나 (theme → {"themeId":"nyang"}) */
@Entity
@Table(name = "tb_app_setting")
public class AppSettingEntity {

  @Id
  @Column(name = "setting_key", length = 50)
  private String key;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "value", nullable = false)
  private Map<String, Object> value;

  @Column(name = "updated_by")
  private Long updatedBy;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected AppSettingEntity() {}

  public AppSettingEntity(String key) {
    this.key = key;
  }

  /** 값과 바꾼 사람·시각을 함께 바꾼다 (updated_at은 도메인이 정한 시각을 그대로 쓴다) */
  public void update(Map<String, Object> value, Long updatedBy, Instant updatedAt) {
    this.value = value;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  public String getKey() {
    return key;
  }

  public Map<String, Object> getValue() {
    return value;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
