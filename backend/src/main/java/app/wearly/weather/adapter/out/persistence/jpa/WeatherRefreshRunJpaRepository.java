package app.wearly.weather.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRefreshRunJpaRepository
    extends JpaRepository<WeatherRefreshRunEntity, Long> {

  /** 끝난 실행 중 가장 최근 것 (시험 호출 제외) */
  Optional<WeatherRefreshRunEntity>
      findFirstByTriggerNotAndFinishedAtIsNotNullOrderByFinishedAtDescRunIdDesc(String trigger);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE WeatherRefreshRunEntity r SET r.status = 'FAILED', r.finishedAt = :now"
          + " WHERE r.status = 'RUNNING' AND r.startedAt < :before")
  int closeAbandoned(@Param("before") Instant before, @Param("now") Instant now);

  /** 호출 이력은 FK ON DELETE CASCADE로 함께 지워진다 */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM WeatherRefreshRunEntity r WHERE r.startedAt < :before")
  int deleteStartedBefore(@Param("before") Instant before);
}
