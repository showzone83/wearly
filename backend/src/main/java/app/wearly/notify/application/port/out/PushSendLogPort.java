package app.wearly.notify.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/** D-1 알림 발송 기록 (JPA). 같은 예정은 하루(한국 날짜)에 한 번만 보낸다 */
public interface PushSendLogPort {

  /**
   * 이 예정을 오늘 보낼 차례로 잡는다. 이미 오늘 보낸(잡은) 예정이면 false. 행을 먼저 넣으므로 서버가 여러 대여도 한 곳만 보낸다
   *
   * @param sendDate 보내는 날 (한국 날짜)
   */
  boolean claim(long planId, long userId, LocalDate plannedDate, LocalDate sendDate);

  /** 보내지 못했을 때 잡은 기록을 푼다 (같은 날 다시 실행하면 다시 보낸다) */
  void release(long planId, LocalDate sendDate);

  /** 이 시각보다 오래된 기록을 지운다. 지운 행 수 */
  int purgeBefore(Instant cutoff);
}
