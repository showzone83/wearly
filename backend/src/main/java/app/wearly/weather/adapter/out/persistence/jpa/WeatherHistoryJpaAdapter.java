package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.WeatherHistoryPort;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherHistory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** WeatherHistoryPort 구현 (JPA) */
@Component
class WeatherHistoryJpaAdapter implements WeatherHistoryPort {

  /** tb_weather_daily_history.cond_text 길이 */
  static final int MAX_COND_TEXT = 100;

  private final WeatherDailyHistoryJpaRepository histories;

  WeatherHistoryJpaAdapter(WeatherDailyHistoryJpaRepository histories) {
    this.histories = histories;
  }

  @Override
  public Optional<WeatherHistory> find(String regionCode, LocalDate date) {
    return histories
        .findById(new WeatherDailyHistoryId(regionCode, date))
        .map(WeatherHistoryJpaAdapter::toDomain);
  }

  @Override
  @Transactional
  public void save(WeatherHistory h) {
    WeatherDailyHistoryEntity e =
        histories
            .findById(new WeatherDailyHistoryId(h.regionCode(), h.date()))
            .orElseGet(() -> new WeatherDailyHistoryEntity(h.regionCode(), h.date()));
    DailyForecast d = h.forecast();
    if (d == null) {
      e.forecast(h.providerCode(), null, null, null, null, null, null, h.forecastFetchedAt());
    } else {
      e.forecast(
          h.providerCode(),
          d.temp(),
          d.min(),
          d.max(),
          d.rainProb(),
          d.cond().name(),
          d.condText() == null || d.condText().length() <= MAX_COND_TEXT
              ? d.condText()
              : d.condText().substring(0, MAX_COND_TEXT),
          h.forecastFetchedAt());
    }
    e.observed(
        h.observedMin(),
        h.observedMax(),
        h.observedTempSum(),
        h.observedCount(),
        h.observedRainCount(),
        h.firstObservedAt(),
        h.lastObservedAt());
    histories.saveAndFlush(e);
  }

  private static WeatherHistory toDomain(WeatherDailyHistoryEntity e) {
    DailyForecast forecast =
        e.getForecastTemp() == null || e.getCond() == null
            ? null
            : new DailyForecast(
                e.getWeatherDate(),
                e.getForecastTemp(),
                e.getForecastMin() == null ? e.getForecastTemp() : e.getForecastMin(),
                e.getForecastMax() == null ? e.getForecastTemp() : e.getForecastMax(),
                e.getRainProb() == null ? 0 : e.getRainProb(),
                WeatherCondition.valueOf(e.getCond()),
                e.getCondText());
    return new WeatherHistory(
        e.getRegionCode(),
        e.getWeatherDate(),
        e.getProviderCode(),
        forecast,
        e.getForecastFetchedAt(),
        e.getObservedMin(),
        e.getObservedMax(),
        e.getObservedTempSum(),
        e.getObservedCount(),
        e.getObservedRainCount(),
        e.getFirstObservedAt(),
        e.getLastObservedAt());
  }
}
