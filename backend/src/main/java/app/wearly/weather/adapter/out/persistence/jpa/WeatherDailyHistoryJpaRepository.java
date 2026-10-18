package app.wearly.weather.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherDailyHistoryJpaRepository
    extends JpaRepository<WeatherDailyHistoryEntity, WeatherDailyHistoryId> {}
