package app.wearly.ai.application.port.out;

import java.time.Instant;

/** AI 사용량 집계 (MyBatis, 읽기 전용) */
public interface AiUsagePort {

  /** [from, to) 구간에 그 회원이 성공한 호출 수 (기능별: ITEM_ANALYZE · OUTFIT_RECOMMEND) */
  long countSuccess(long userId, String feature, Instant from, Instant to);
}
