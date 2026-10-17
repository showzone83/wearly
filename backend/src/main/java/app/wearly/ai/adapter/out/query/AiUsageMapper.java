package app.wearly.ai.adapter.out.query;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 사용량 집계 (MyBatis 읽기 전용). SQL은 resources/mapper/ai/AiUsageMapper.xml */
@Mapper
public interface AiUsageMapper {

  /** [from, to) 구간의 그 기능 성공 호출 수 */
  long countSuccess(
      @Param("userId") long userId,
      @Param("feature") String feature,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** 최근 기록 (운영 확인·테스트용) */
  List<AiLogRow> findRecent(@Param("userId") long userId, @Param("limit") int limit);
}
