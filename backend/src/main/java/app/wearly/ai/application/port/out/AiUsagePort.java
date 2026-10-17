package app.wearly.ai.application.port.out;

import java.time.Instant;

/** AI 사용량 집계 (MyBatis, 읽기 전용) */
public interface AiUsagePort {

  /** [from, to) 구간에 그 회원이 성공한 사진 분석(ITEM_ANALYZE) 수 */
  long countSuccess(long userId, Instant from, Instant to);
}
