package app.wearly.notify.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_push_token JPA 엔티티 (회원·기기당 하나, 같은 토큰은 한 행) */
@Entity
@Table(name = "tb_push_token")
public class PushTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "push_token_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "device_id", nullable = false, length = 64, updatable = false)
  private String deviceId;

  @Column(name = "platform", nullable = false, length = 10)
  private String platform;

  @Column(name = "token", nullable = false, length = 255)
  private String token;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PushTokenEntity() {}

  PushTokenEntity(Long userId, String deviceId, String platform, String token, Instant now) {
    this.userId = userId;
    this.deviceId = deviceId;
    this.platform = platform;
    this.token = token;
    this.createdAt = now;
    this.updatedAt = now;
  }

  void change(String platform, String token, Instant now) {
    this.platform = platform;
    this.token = token;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getPlatform() {
    return platform;
  }

  public String getToken() {
    return token;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
