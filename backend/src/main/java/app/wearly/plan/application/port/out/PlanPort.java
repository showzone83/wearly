package app.wearly.plan.application.port.out;

import app.wearly.plan.domain.Plan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 예정 쓰기·단건 조회 (JPA) */
public interface PlanPort {

  /** 내 예정 (남의 것·없는 것은 빈 값) */
  Optional<Plan> find(long userId, long planId);

  /** 내 예정 중 clientId가 이 목록에 있는 것 */
  List<Plan> findByClientIds(long userId, Collection<String> clientIds);

  Plan save(Plan plan);

  void delete(Plan plan);
}
