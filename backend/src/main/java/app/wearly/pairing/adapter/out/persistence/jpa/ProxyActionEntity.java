package app.wearly.pairing.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_closet_proxy_action JPA 엔티티 (V28, 대리 작업 기록). 쓰기만 하고 읽기는 MyBatis가 한다 */
@Entity
@Table(name = "tb_closet_proxy_action")
public class ProxyActionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "action_id")
  private Long id;

  @Column(name = "pairing_id", nullable = false, updatable = false)
  private Long pairingId;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private Long ownerId;

  @Column(name = "helper_id", nullable = false, updatable = false)
  private Long helperId;

  @Column(name = "action", nullable = false, length = 20, updatable = false)
  private String action;

  @Column(name = "item_id", nullable = false, updatable = false)
  private Long itemId;

  @Column(name = "item_name", length = 40, updatable = false)
  private String itemName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "notified_at")
  private Instant notifiedAt;

  protected ProxyActionEntity() {}

  public ProxyActionEntity(
      Long pairingId,
      Long ownerId,
      Long helperId,
      String action,
      Long itemId,
      String itemName,
      Instant createdAt) {
    this.pairingId = pairingId;
    this.ownerId = ownerId;
    this.helperId = helperId;
    this.action = action;
    this.itemId = itemId;
    this.itemName = itemName;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Instant getNotifiedAt() {
    return notifiedAt;
  }
}
