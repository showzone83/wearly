package app.wearly.user.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_user_withdrawal JPA 엔티티 (V30, 탈퇴 기록). tb_user에 FK가 없어 회원이 지워져도 남는다. 시각은 서비스가 정해 넘긴다 */
@Entity
@Table(name = "tb_user_withdrawal")
public class WithdrawalEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "withdrawal_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "reason", nullable = false, length = 20, updatable = false)
  private String reason;

  @Column(name = "reason_text", length = 200, updatable = false)
  private String reasonText;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "purge_after", nullable = false, updatable = false)
  private Instant purgeAfter;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  @Column(name = "canceled_by", length = 10)
  private String canceledBy;

  @Column(name = "purged_at")
  private Instant purgedAt;

  @Column(name = "purged_by", length = 10)
  private String purgedBy;

  protected WithdrawalEntity() {}

  public WithdrawalEntity(
      Long userId, String reason, String reasonText, Instant requestedAt, Instant purgeAfter) {
    this.userId = userId;
    this.reason = reason;
    this.reasonText = reasonText;
    this.requestedAt = requestedAt;
    this.purgeAfter = purgeAfter;
  }

  /** 닫힌 시각·닫은 쪽을 덮어쓴다 */
  public void close(Instant canceledAt, String canceledBy, Instant purgedAt, String purgedBy) {
    this.canceledAt = canceledAt;
    this.canceledBy = canceledBy;
    this.purgedAt = purgedAt;
    this.purgedBy = purgedBy;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getReason() {
    return reason;
  }

  public String getReasonText() {
    return reasonText;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getPurgeAfter() {
    return purgeAfter;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public String getCanceledBy() {
    return canceledBy;
  }

  public Instant getPurgedAt() {
    return purgedAt;
  }

  public String getPurgedBy() {
    return purgedBy;
  }
}
