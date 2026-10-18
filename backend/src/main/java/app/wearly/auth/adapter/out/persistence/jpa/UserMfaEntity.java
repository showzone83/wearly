package app.wearly.auth.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_user_mfa JPA 엔티티 (관리자 TOTP 등록, V15). 마지막 사용 단계는 MfaJpaRepository의 조건부 UPDATE로 바꾼다 */
@Entity
@Table(name = "tb_user_mfa")
public class UserMfaEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "secret_cipher", nullable = false)
  private byte[] secretCipher;

  @Column(name = "secret_nonce", nullable = false)
  private byte[] secretNonce;

  @Column(name = "key_version", nullable = false)
  private short keyVersion;

  @Column(name = "last_used_step")
  private Long lastUsedStep;

  @Column(name = "enrolled_at", nullable = false)
  private Instant enrolledAt;

  protected UserMfaEntity() {}

  public UserMfaEntity(
      Long userId,
      byte[] secretCipher,
      byte[] secretNonce,
      int keyVersion,
      Long lastUsedStep,
      Instant enrolledAt) {
    this.userId = userId;
    this.secretCipher = secretCipher;
    this.secretNonce = secretNonce;
    this.keyVersion = (short) keyVersion;
    this.lastUsedStep = lastUsedStep;
    this.enrolledAt = enrolledAt;
  }

  public Long getUserId() {
    return userId;
  }

  public byte[] getSecretCipher() {
    return secretCipher;
  }

  public byte[] getSecretNonce() {
    return secretNonce;
  }

  public int getKeyVersion() {
    return keyVersion;
  }

  public Long getLastUsedStep() {
    return lastUsedStep;
  }

  public Instant getEnrolledAt() {
    return enrolledAt;
  }
}
