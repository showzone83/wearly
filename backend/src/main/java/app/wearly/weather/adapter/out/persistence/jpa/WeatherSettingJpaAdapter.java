package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.WeatherSettingPort;
import app.wearly.weather.domain.WeatherSetting;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** WeatherSettingPort 구현 (JPA) */
@Component
class WeatherSettingJpaAdapter implements WeatherSettingPort {

  private final WeatherSettingJpaRepository settings;

  WeatherSettingJpaAdapter(WeatherSettingJpaRepository settings) {
    this.settings = settings;
  }

  @Override
  public Optional<WeatherSetting> load() {
    return settings.findById(WeatherSettingEntity.ID).map(WeatherSettingJpaAdapter::toDomain);
  }

  @Override
  public WeatherSetting save(WeatherSetting s) {
    WeatherSettingEntity e =
        settings
            .findById(WeatherSettingEntity.ID)
            .orElseGet(() -> new WeatherSettingEntity(WeatherSettingEntity.ID));
    e.update(
        s.enabled(),
        s.intervalMinutes(),
        s.forecastDays(),
        s.failureThreshold(),
        s.cooldownMinutes(),
        s.updatedBy(),
        s.updatedAt());
    return toDomain(settings.saveAndFlush(e));
  }

  private static WeatherSetting toDomain(WeatherSettingEntity e) {
    return new WeatherSetting(
        e.isEnabled(),
        e.getIntervalMinutes(),
        e.getForecastDays(),
        e.getFailureThreshold(),
        e.getCooldownMinutes(),
        e.getUpdatedBy(),
        e.getUpdatedAt());
  }
}
