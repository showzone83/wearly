package app.wearly.pairing.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_closet_pairing JPA 엔티티 (V27, 옷장 도우미 연결). 시각은 서비스가 정해 넘긴다 */
@Entity
@Table(name = "tb_closet_pairing")
public class PairingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "pairing_id")
  private Long id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private Long ownerId;

  @Column(name = "helper_id", nullable = false, updatable = false)
  private Long helperId;

  @Column(name = "permission", length = 10)
  private String permission;

  @Column(name = "status", nullable = false, length = 10)
  private String status;

  @Column(name = "issued_by", nullable = false, length = 10, updatable = false)
  private String issuedBy;

  @Column(name = "notify_owner", nullable = false)
  private boolean notifyOwner;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "ended_by", length = 10)
  private String endedBy;

  protected PairingEntity() {}

  public PairingEntity(Long ownerId, Long helperId, String issuedBy, Instant createdAt) {
    this.ownerId = ownerId;
    this.helperId = helperId;
    this.issuedBy = issuedBy;
    this.createdAt = createdAt;
  }

  /** 바뀌는 값을 모두 덮어쓴다 */
  public void apply(
      String permission,
      String status,
      boolean notifyOwner,
      Instant approvedAt,
      Instant endedAt,
      String endedBy) {
    this.permission = permission;
    this.status = status;
    this.notifyOwner = notifyOwner;
    this.approvedAt = approvedAt;
    this.endedAt = endedAt;
    this.endedBy = endedBy;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public Long getHelperId() {
    return helperId;
  }

  public String getPermission() {
    return permission;
  }

  public String getStatus() {
    return status;
  }

  public String getIssuedBy() {
    return issuedBy;
  }

  public boolean isNotifyOwner() {
    return notifyOwner;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public String getEndedBy() {
    return endedBy;
  }
}
