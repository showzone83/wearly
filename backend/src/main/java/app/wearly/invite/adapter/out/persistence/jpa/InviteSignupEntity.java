package app.wearly.invite.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_invite_signup JPA 엔티티 (초대 코드 가입 이력 한 줄, 가입자당 하나) */
@Entity
@Table(name = "tb_invite_signup")
public class InviteSignupEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "invite_signup_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "referrer_id")
  private Long referrerId;

  @Column(name = "entered_code", nullable = false, length = 20, updatable = false)
  private String enteredCode;

  @Column(name = "result", nullable = false, length = 20, updatable = false)
  private String result;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InviteSignupEntity() {}

  InviteSignupEntity(
      Long userId, Long referrerId, String enteredCode, String result, Instant createdAt) {
    this.userId = userId;
    this.referrerId = referrerId;
    this.enteredCode = enteredCode;
    this.result = result;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getReferrerId() {
    return referrerId;
  }

  public String getEnteredCode() {
    return enteredCode;
  }

  public String getResult() {
    return result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
