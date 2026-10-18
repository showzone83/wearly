package app.wearly.weather.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherPlaceSnapshotJpaRepository
    extends JpaRepository<WeatherPlaceSnapshotEntity, String> {}
