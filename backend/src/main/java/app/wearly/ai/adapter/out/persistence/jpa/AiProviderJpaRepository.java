package app.wearly.ai.adapter.out.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiProviderJpaRepository extends JpaRepository<AiProviderEntity, String> {

  List<AiProviderEntity> findAllByOrderByPriorityAscProviderIdAsc();

  @Query("select coalesce(max(p.priority), 0) from AiProviderEntity p")
  int maxPriority();
}
