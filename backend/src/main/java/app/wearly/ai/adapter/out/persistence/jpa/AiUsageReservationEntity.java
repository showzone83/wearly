package app.wearly.ai.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** tb_ai_usage_reservation JPA 엔티티: 진행 중인 AI 호출의 일일 한도 예약 (M7) */
@Entity
@Table(name = "tb_ai_usage_reservation")
public class AiUsageReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "reservation_id")
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "feature", nullable = false, length = 30)
  private String feature;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AiUsageReservationEntity() {}

  public AiUsageReservationEntity(Long userId, String feature, Instant createdAt) {
    this.userId = userId;
    this.feature = feature;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }
}
