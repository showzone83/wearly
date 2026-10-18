package app.wearly.plan.adapter.out.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** tb_planned_outfit 리포지토리 (쓰기·단건 조회) */
public interface PlanJpaRepository extends JpaRepository<PlanEntity, Long> {

  Optional<PlanEntity> findByIdAndUserId(Long id, Long userId);

  List<PlanEntity> findByUserIdAndClientIdIn(Long userId, Collection<String> clientIds);
}
