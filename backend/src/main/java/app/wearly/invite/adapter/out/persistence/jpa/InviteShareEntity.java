package app.wearly.invite.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_invite_share JPA 엔티티 (초대한 이력 한 줄) */
@Entity
@Table(name = "tb_invite_share")
public class InviteShareEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "invite_share_id")
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "channel", nullable = false, length = 20, updatable = false)
  private String channel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InviteShareEntity() {}

  InviteShareEntity(Long userId, String channel, Instant createdAt) {
    this.userId = userId;
    this.channel = channel;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getChannel() {
    return channel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
