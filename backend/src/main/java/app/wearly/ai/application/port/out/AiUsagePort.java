package app.wearly.ai.application.port.out;

import java.time.Instant;

/** AI 사용량 집계와 한도 확인 잠금 (MyBatis) */
public interface AiUsagePort {

  /** [from, to) 구간에 그 회원이 그 기능으로 성공한 호출 수 */
  long countSuccess(long userId, String feature, Instant from, Instant to);

  /** since 뒤에 만든 진행 중 예약 수 */
  long countReservations(long userId, String feature, Instant since);

  /** 같은 회원·기능의 한도 확인을 지금 트랜잭션이 끝날 때까지 한 줄로 세운다 (트랜잭션 안에서만) */
  void lockQuota(long userId, String feature);
}
