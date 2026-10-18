package app.wearly.plan.application.port.out;

import app.wearly.plan.application.port.out.dto.PlanCodeCountRowDto;
import app.wearly.plan.domain.Plan;
import java.time.LocalDate;
import java.util.List;

/** 예정 목록·개수·코드 사용 수 (MyBatis) */
public interface PlanQueryPort {

  List<Plan> findPlans(PlanFilter filter);

  /** 이 날짜의 모든 회원 예정 (알림용, 회원 ID → 시간대 → ID 순) */
  List<Plan> findOnDate(LocalDate date);

  long count(long userId);

  /** 회원별 예정 쓰기 잠금 (pg_advisory_xact_lock). 개수 상한·clientId 중복을 동시 요청에도 지킨다. 트랜잭션이 끝나면 풀린다 */
  void lockUser(long userId);

  List<PlanCodeCountRowDto> countByCode();

  long countUsing(String group, String code);
}
