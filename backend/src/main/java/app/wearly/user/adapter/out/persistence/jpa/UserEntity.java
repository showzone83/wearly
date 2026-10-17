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

/** tb_user JPA 엔티티. 도메인 모델(User)과는 UserJpaAdapter가 바꾼다 */
@Entity
@Table(name = "tb_user")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long id;

  @Column(name = "email")
  private String email;

  @Column(name = "name", nullable = false, length = 20)
  private String name;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "role", nullable = false, length = 20)
  private String role = "USER";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserEntity() {}

  public UserEntity(String email, String name) {
    this.email = email;
    this.name = name;
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

  public void changeStatus(String status) {
    this.status = status;
  }

  public void changeRole(String role) {
    this.role = role;
  }

  public void rename(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public String getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
