package app.wearly.ai.adapter.out.persistence.jpa;

import app.wearly.ai.application.port.out.AiReservationPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** AiReservationPort 구현 (JPA). 쓰기는 flush하고 돌아온다 */
@Component
class AiReservationJpaAdapter implements AiReservationPort {

  private final AiUsageReservationJpaRepository reservations;

  AiReservationJpaAdapter(AiUsageReservationJpaRepository reservations) {
    this.reservations = reservations;
  }

  @Override
  public long reserve(long userId, String feature, Instant at) {
    return reservations.saveAndFlush(new AiUsageReservationEntity(userId, feature, at)).getId();
  }

  @Override
  public void release(long reservationId) {
    reservations.deleteById(reservationId);
    reservations.flush();
  }

  @Override
  public void deleteOlderThan(long userId, String feature, Instant before) {
    reservations.deleteOlderThan(userId, feature, before);
  }
}
