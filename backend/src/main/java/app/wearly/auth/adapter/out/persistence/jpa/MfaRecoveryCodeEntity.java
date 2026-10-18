package app.wearly.auth.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * tb_user_mfa_recovery_code JPA 엔티티 (복구 코드 해시, V15). 쓰기는 조건부 UPDATE(MfaRecoveryCodeJpaRepository)로
 * 한다
 */
@Entity
@Table(name = "tb_user_mfa_recovery_code")
public class MfaRecoveryCodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "recovery_code_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MfaRecoveryCodeEntity() {}

  public MfaRecoveryCodeEntity(Long userId, String codeHash, Instant createdAt) {
    this.userId = userId;
    this.codeHash = codeHash;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }
}
