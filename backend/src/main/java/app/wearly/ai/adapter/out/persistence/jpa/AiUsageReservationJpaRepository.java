package app.wearly.ai.adapter.out.persistence.jpa;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageReservationJpaRepository
    extends JpaRepository<AiUsageReservationEntity, Long> {

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from AiUsageReservationEntity r"
          + " where r.userId = :userId and r.feature = :feature and r.createdAt < :before")
  int deleteOlderThan(
      @Param("userId") Long userId,
      @Param("feature") String feature,
      @Param("before") Instant before);
}
