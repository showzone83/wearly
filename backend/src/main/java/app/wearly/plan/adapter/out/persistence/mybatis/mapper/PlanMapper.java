package app.wearly.plan.adapter.out.persistence.mybatis.mapper;

import app.wearly.plan.adapter.out.persistence.mybatis.dto.PlanRowDto;
import app.wearly.plan.application.port.out.PlanFilter;
import app.wearly.plan.application.port.out.dto.PlanCodeCountRowDto;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 예정 읽기 전용 조회 (MyBatis): 목록·개수·코드 사용 수·회원 잠금. SQL은 resources/mapper/plan/PlanMapper.xml */
@Mapper
public interface PlanMapper {

  List<PlanRowDto> findPlans(PlanFilter filter);

  /** 이 날짜의 모든 회원 예정 (알림용) */
  List<PlanRowDto> findOnDate(@Param("date") LocalDate date);

  long count(@Param("userId") long userId);

  /** 회원별 예정 쓰기 잠금 (pg_advisory_xact_lock). 트랜잭션이 끝나면 풀린다 */
  Integer lockUser(@Param("userId") long userId);

  List<PlanCodeCountRowDto> countByCode();

  long countUsing(@Param("group") String group, @Param("code") String code);
}
