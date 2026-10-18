package app.wearly.notify.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * tb_push_send_log JPA 엔티티 (D-1 알림 발송 기록). 넣기는 PushSendLogJpaRepository.claim(네이티브 SQL, ON CONFLICT
 * DO NOTHING)으로 하고, 이 엔티티는 스키마 검증(ddl-auto: validate)과 삭제용이다.
 */
@Entity
@Table(name = "tb_push_send_log")
public class PushSendLogEntity {

  @Id
  @Column(name = "push_send_log_id")
  private Long id;

  @Column(name = "planned_outfit_id", nullable = false)
  private Long plannedOutfitId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "notify_kind", nullable = false, length = 20)
  private String notifyKind;

  @Column(name = "planned_date", nullable = false)
  private LocalDate plannedDate;

  @Column(name = "send_date", nullable = false)
  private LocalDate sendDate;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PushSendLogEntity() {}

  public Long getId() {
    return id;
  }

  public Long getPlannedOutfitId() {
    return plannedOutfitId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getNotifyKind() {
    return notifyKind;
  }

  public LocalDate getPlannedDate() {
    return plannedDate;
  }

  public LocalDate getSendDate() {
    return sendDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
