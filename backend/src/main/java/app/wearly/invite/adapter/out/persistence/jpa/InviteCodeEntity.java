package app.wearly.invite.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * tb_user_invite_code JPA 엔티티 (회원별 초대 코드). 넣기는 InviteCodeJpaRepository.insertIfAbsent(네이티브 SQL, ON
 * CONFLICT DO NOTHING)로 한다
 */
@Entity
@Table(name = "tb_user_invite_code")
public class InviteCodeEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "code", nullable = false, length = 8)
  private String code;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InviteCodeEntity() {}

  public Long getUserId() {
    return userId;
  }

  public String getCode() {
    return code;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
