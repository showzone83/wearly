package app.wearly.weather.adapter.out.persistence.jpa;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherCallLogJpaRepository extends JpaRepository<WeatherCallLogEntity, Long> {

  /** 실행 밖 호출(ON_DEMAND·SEARCH·CLIMATE)은 실행과 함께 지워지지 않으므로 시각으로 지운다 */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM WeatherCallLogEntity c WHERE c.runId IS NULL AND c.calledAt < :before")
  int deleteWithoutRunBefore(@Param("before") Instant before);
}
