package app.wearly.ai.application.port.out;

import java.time.Instant;

/** 일일 한도 예약 쓰기 (JPA, tb_ai_usage_reservation) */
public interface AiReservationPort {

  /**
   * @return 예약 ID
   */
  long reserve(long userId, String feature, Instant at);

  void release(long reservationId);

  /** 서버가 죽어 남은 오래된 예약을 지운다 */
  void deleteOlderThan(long userId, String feature, Instant before);
}
