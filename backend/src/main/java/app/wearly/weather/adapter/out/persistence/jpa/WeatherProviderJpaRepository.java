package app.wearly.weather.adapter.out.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherProviderJpaRepository extends JpaRepository<WeatherProviderEntity, String> {

  List<WeatherProviderEntity> findAllByOrderByPriorityAscCodeAsc();
}
