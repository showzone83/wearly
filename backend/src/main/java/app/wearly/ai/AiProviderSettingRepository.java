package app.wearly.ai;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiProviderSettingRepository extends JpaRepository<AiProviderSetting, String> {

    /** 우선순위(작을수록 먼저) → ID 순 */
    List<AiProviderSetting> findAllByOrderByPriorityAscProviderIdAsc();

    @Query("select coalesce(max(p.priority), 0) from AiProviderSetting p")
    int maxPriority();
}
