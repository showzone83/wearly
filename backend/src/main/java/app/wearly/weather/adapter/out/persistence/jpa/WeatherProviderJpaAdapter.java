package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.WeatherProviderPort;
import app.wearly.weather.domain.ProviderHealth;
import app.wearly.weather.domain.WeatherProvider;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** WeatherProviderPort 구현 (JPA). 설정 칸과 상태 칸을 따로 저장한다 (엔티티 @DynamicUpdate) */
@Component
class WeatherProviderJpaAdapter implements WeatherProviderPort {

  /** tb_weather_provider.last_error 길이 */
  static final int MAX_ERROR = 300;

  private final WeatherProviderJpaRepository providers;

  WeatherProviderJpaAdapter(WeatherProviderJpaRepository providers) {
    this.providers = providers;
  }

  @Override
  public List<WeatherProvider> findAll() {
    return providers.findAllByOrderByPriorityAscCodeAsc().stream()
        .map(WeatherProviderJpaAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<WeatherProvider> find(String code) {
    return code == null
        ? Optional.empty()
        : providers.findById(code).map(WeatherProviderJpaAdapter::toDomain);
  }

  @Override
  public WeatherProvider saveConfig(WeatherProvider p) {
    WeatherProviderEntity e = load(p.code());
    e.configure(
        p.enabled(),
        p.priority(),
        p.monthlyQuota(),
        p.maxForecastDays(),
        p.updatedBy(),
        p.updatedAt());
    return toDomain(providers.saveAndFlush(e));
  }

  /** 실행은 트랜잭션 밖에서 부르므로 읽기·저장을 한 트랜잭션으로 묶는다 */
  @Override
  @Transactional
  public void saveHealth(String code, ProviderHealth h) {
    WeatherProviderEntity e = load(code);
    e.health(
        h.consecutiveFailures(),
        h.circuitReason(),
        h.circuitOpenUntil(),
        h.lastSuccessAt(),
        h.lastFailureAt(),
        truncate(h.lastError()));
    providers.saveAndFlush(e);
  }

  private WeatherProviderEntity load(String code) {
    return providers.findById(code).orElseThrow(() -> new NoSuchElementException(code));
  }

  private static WeatherProvider toDomain(WeatherProviderEntity e) {
    return new WeatherProvider(
        e.getCode(),
        e.getDisplayName(),
        e.isEnabled(),
        e.getPriority(),
        e.getKeyName(),
        e.getMonthlyQuota(),
        e.getMaxForecastDays(),
        new ProviderHealth(
            e.getConsecutiveFailures(),
            e.getCircuitReason(),
            e.getCircuitOpenUntil(),
            e.getLastSuccessAt(),
            e.getLastFailureAt(),
            e.getLastError()),
        e.getUpdatedBy(),
        e.getUpdatedAt());
  }

  private static String truncate(String s) {
    return s == null || s.length() <= MAX_ERROR ? s : s.substring(0, MAX_ERROR - 1) + "…";
  }
}
