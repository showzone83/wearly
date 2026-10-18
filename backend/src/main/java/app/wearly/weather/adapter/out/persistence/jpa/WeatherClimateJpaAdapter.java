package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.ClimatePort;
import app.wearly.weather.domain.MonthlyClimate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ClimatePort 구현 (JPA). 기온은 소수 둘째 자리, 비율은 셋째 자리로 반올림해 저장한다 */
@Component
class WeatherClimateJpaAdapter implements ClimatePort {

  private final WeatherClimateJpaRepository climates;

  WeatherClimateJpaAdapter(WeatherClimateJpaRepository climates) {
    this.climates = climates;
  }

  @Override
  public Optional<MonthlyClimate> find(String gridKey, int month) {
    return climates
        .findById(new WeatherClimateEntity.Key(gridKey, (short) month))
        .map(WeatherClimateJpaAdapter::toDomain);
  }

  @Override
  @Transactional
  public void saveAll(List<MonthlyClimate> months) {
    for (MonthlyClimate m : months) {
      WeatherClimateEntity e =
          climates
              .findById(new WeatherClimateEntity.Key(m.gridKey(), (short) m.month()))
              .orElseGet(() -> new WeatherClimateEntity(m.gridKey(), (short) m.month()));
      e.fill(
          round(m.tempAvg(), 100),
          round(m.tempMin(), 100),
          round(m.tempMax(), 100),
          round(m.rainDayRatio(), 1000),
          m.years(),
          m.source(),
          m.computedAt());
      climates.save(e);
    }
    climates.flush();
  }

  private static double round(double v, int scale) {
    return Math.round(v * scale) / (double) scale;
  }

  private static MonthlyClimate toDomain(WeatherClimateEntity e) {
    return new MonthlyClimate(
        e.getGridKey(),
        e.getMonth(),
        e.getTempAvg(),
        e.getTempMin(),
        e.getTempMax(),
        e.getRainDayRatio(),
        e.getYears(),
        e.getSource(),
        e.getComputedAt());
  }
}
