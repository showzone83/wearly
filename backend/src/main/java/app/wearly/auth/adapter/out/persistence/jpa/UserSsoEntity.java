package app.wearly.auth.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_user_sso JPA 엔티티 (회원 ↔ IdP 계정, V22). 회원과는 user_id 값으로만 잇는다 */
@Entity
@Table(name = "tb_user_sso")
public class UserSsoEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_sso_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "provider", nullable = false, length = 50)
  private String provider;

  @Column(name = "issuer", nullable = false, length = 500, updatable = false)
  private String issuer;

  @Column(name = "subject", nullable = false, length = 255, updatable = false)
  private String subject;

  @Column(name = "email")
  private String email;

  @Column(name = "connected_at", nullable = false, updatable = false)
  private Instant connectedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected UserSsoEntity() {}

  public UserSsoEntity(
      Long userId, String provider, String issuer, String subject, String email, Instant at) {
    this.userId = userId;
    this.provider = provider;
    this.issuer = issuer;
    this.subject = subject;
    this.email = email;
    this.connectedAt = at;
  }

  public void touch(String email, String provider, Instant at) {
    if (email != null) {
      this.email = email;
    }
    this.provider = provider;
    this.lastLoginAt = at;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getProvider() {
    return provider;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getSubject() {
    return subject;
  }

  public String getEmail() {
    return email;
  }
}
