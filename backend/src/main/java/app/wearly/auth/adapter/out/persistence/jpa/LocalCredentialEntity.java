package app.wearly.auth.adapter.out.persistence.jpa;

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

/**
 * tb_user_local_credential JPA 엔티티 (임시 수단). 소셜 로그인과 같은 자리(회원 하위)에 둔다. 회원(user 컨텍스트)과는 user_id 값으로만
 * 잇는다 (다른 컨텍스트의 엔티티를 참조하지 않는다).
 */
@Entity
@Table(name = "tb_user_local_credential")
public class LocalCredentialEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "credential_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  @Column(name = "password_postponed_at")
  private Instant passwordPostponedAt;

  @Column(name = "password_reminded_at")
  private Instant passwordRemindedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LocalCredentialEntity() {}

  public LocalCredentialEntity(Long userId, String email, String passwordHash) {
    this.userId = userId;
    this.email = email;
    this.passwordHash = passwordHash;
  }

  @PrePersist
  void onCreate() {
    createdAt = PersistenceClock.now();
    updatedAt = createdAt;
    if (passwordChangedAt == null) {
      passwordChangedAt = createdAt;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = PersistenceClock.now();
  }

  public void markLogin(Instant at) {
    this.lastLoginAt = at;
  }

  /** 새 비밀번호: 기준 시각을 바꾸고 연장·알림 기록을 지운다 (28 문서) */
  public void changePassword(String passwordHash, Instant at) {
    this.passwordHash = passwordHash;
    this.passwordChangedAt = at;
    this.passwordPostponedAt = null;
    this.passwordRemindedAt = null;
  }

  public void postpone(Instant at) {
    this.passwordPostponedAt = at;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public Instant getPasswordPostponedAt() {
    return passwordPostponedAt;
  }
}
