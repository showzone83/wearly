package app.wearly.ai.adapter.out.persistence.mybatis.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 사용량 집계와 한도 확인 잠금 (MyBatis). SQL은 resources/mapper/ai/AiQuotaMapper.xml */
@Mapper
public interface AiQuotaMapper {

  /** [from, to) 구간의 그 기능 성공 호출 수 */
  long countSuccess(
      @Param("userId") long userId,
      @Param("feature") String feature,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** since 뒤에 만든 그 기능의 진행 중 예약 수 */
  long countReservations(
      @Param("userId") long userId,
      @Param("feature") String feature,
      @Param("since") Instant since);

  /** 회원·기능별 트랜잭션 잠금 (pg_advisory_xact_lock). 트랜잭션이 끝나면 풀린다 */
  int lockQuota(@Param("userId") long userId, @Param("feature") String feature);
}
