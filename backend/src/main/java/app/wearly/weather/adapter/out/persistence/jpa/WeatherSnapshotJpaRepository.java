package app.wearly.weather.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeatherSnapshotJpaRepository extends JpaRepository<WeatherSnapshotEntity, String> {

  @Query("SELECT max(s.fetchedAt) FROM WeatherSnapshotEntity s")
  Optional<Instant> findLatestFetchedAt();
}
