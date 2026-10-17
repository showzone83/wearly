package app.wearly.ai;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 사용량 집계 (MyBatis 읽기 전용). SQL은 resources/mapper/AiUsageMapper.xml */
@Mapper
public interface AiUsageMapper {

    /**
     * [from, to) 구간에서 한도로 세는 호출 수: 성공 + 진행 중(pendingSince 이후 시작).
     * 실패한 호출은 세지 않는다.
     */
    long countUsed(@Param("userId") long userId, @Param("from") Instant from, @Param("to") Instant to,
            @Param("pendingSince") Instant pendingSince);

    /** 최근 기록 (운영 확인·테스트용) */
    List<AiLogRow> findRecent(@Param("userId") long userId, @Param("limit") int limit);
}
