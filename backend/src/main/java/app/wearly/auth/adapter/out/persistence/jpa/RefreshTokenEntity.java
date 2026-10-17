package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.common.persistence.PersistenceClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** tb_refresh_token JPA 엔티티. 회전·재사용 규칙은 도메인 RefreshToken에 있다 */
@Entity
@Table(name = "tb_refresh_token")
public class RefreshTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "refresh_token_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by_id")
  private Long replacedById;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RefreshTokenEntity() {}

  public RefreshTokenEntity(Long userId, String tokenHash, UUID familyId, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onCreate() {
    createdAt = PersistenceClock.now();
  }

  /** 도메인 모델의 폐기·회전 상태를 옮긴다 (한 번 정해진 값은 도메인이 바꾸지 않는다) */
  void applyState(Instant revokedAt, Long replacedById) {
    this.revokedAt = revokedAt;
    this.replacedById = replacedById;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Long getReplacedById() {
    return replacedById;
  }
}
