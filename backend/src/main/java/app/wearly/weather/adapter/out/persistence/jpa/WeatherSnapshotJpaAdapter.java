package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.WeatherSnapshotPort;
import app.wearly.weather.domain.CurrentWeather;
import app.wearly.weather.domain.DailyForecast;
import app.wearly.weather.domain.WeatherCondition;
import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * WeatherSnapshotPort 구현 (JPA). JSONB 모양 (17 문서 2장):
 *
 * <ul>
 *   <li>current: {temp, feelsLike, cond, condText, observedAt(ISO-8601 UTC)}
 *   <li>days: [{date(YYYY-MM-DD), temp, min, max, rainProb, cond, condText}]
 * </ul>
 */
@Component
class WeatherSnapshotJpaAdapter implements WeatherSnapshotPort {

  static final int MAX_ERROR = 300;

  private final WeatherSnapshotJpaRepository snapshots;

  WeatherSnapshotJpaAdapter(WeatherSnapshotJpaRepository snapshots) {
    this.snapshots = snapshots;
  }

  @Override
  public Optional<WeatherSnapshot> find(String regionCode) {
    return regionCode == null
        ? Optional.empty()
        : snapshots.findById(regionCode).map(WeatherSnapshotJpaAdapter::toDomain);
  }

  @Override
  public List<WeatherSnapshot> findAll() {
    return snapshots.findAll().stream().map(WeatherSnapshotJpaAdapter::toDomain).toList();
  }

  @Override
  @Transactional
  public void save(WeatherSnapshot s) {
    WeatherSnapshotEntity e = entity(s.regionCode());
    e.fill(
        s.providerCode(),
        current(s.current()),
        s.days().stream().map(WeatherSnapshotJpaAdapter::day).toList(),
        s.raw(),
        truncate(s.locationName(), 100),
        s.fetchedAt());
    snapshots.saveAndFlush(e);
  }

  @Override
  @Transactional
  public void recordError(String regionCode, String error, Instant at) {
    WeatherSnapshotEntity e = entity(regionCode);
    e.error(truncate(error, MAX_ERROR), at);
    snapshots.saveAndFlush(e);
  }

  @Override
  public Optional<Instant> latestFetchedAt() {
    return snapshots.findLatestFetchedAt();
  }

  private WeatherSnapshotEntity entity(String regionCode) {
    return snapshots.findById(regionCode).orElseGet(() -> new WeatherSnapshotEntity(regionCode));
  }

  static Map<String, Object> current(CurrentWeather c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("temp", c.temp());
    m.put("feelsLike", c.feelsLike());
    m.put("cond", c.cond().name());
    m.put("condText", c.condText());
    m.put("observedAt", c.observedAt() == null ? null : c.observedAt().toString());
    return m;
  }

  static Map<String, Object> day(DailyForecast d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("date", d.date().toString());
    m.put("temp", d.temp());
    m.put("min", d.min());
    m.put("max", d.max());
    m.put("rainProb", d.rainProb());
    m.put("cond", d.cond().name());
    m.put("condText", d.condText());
    return m;
  }

  private static WeatherSnapshot toDomain(WeatherSnapshotEntity e) {
    return new WeatherSnapshot(
        e.getRegionCode(),
        e.getProviderCode(),
        currentOf(e.getCurrent()),
        daysOf(e.getDays()),
        e.getRaw(),
        e.getLocationName(),
        e.getFetchedAt(),
        e.getLastError(),
        e.getLastErrorAt());
  }

  /** JSONB current → 도메인 (장소 캐시 tb_weather_place_snapshot도 같은 모양) */
  static CurrentWeather currentOf(Map<String, Object> c) {
    return c == null
        ? null
        : new CurrentWeather(
            number(c.get("temp")),
            c.get("feelsLike") instanceof Number n ? n.doubleValue() : null,
            cond(c.get("cond")),
            text(c.get("condText")),
            c.get("observedAt") instanceof String s ? Instant.parse(s) : null);
  }

  /** JSONB days → 도메인 */
  static List<DailyForecast> daysOf(List<Map<String, Object>> days) {
    return days == null
        ? List.of()
        : days.stream()
            .map(
                d ->
                    new DailyForecast(
                        LocalDate.parse(String.valueOf(d.get("date"))),
                        number(d.get("temp")),
                        number(d.get("min")),
                        number(d.get("max")),
                        (int) number(d.get("rainProb")),
                        cond(d.get("cond")),
                        text(d.get("condText"))))
            .toList();
  }

  private static double number(Object v) {
    return v instanceof Number n ? n.doubleValue() : 0;
  }

  private static String text(Object v) {
    return v instanceof String s ? s : null;
  }

  private static WeatherCondition cond(Object v) {
    try {
      return v instanceof String s ? WeatherCondition.valueOf(s) : WeatherCondition.CLOUDY;
    } catch (IllegalArgumentException e) {
      return WeatherCondition.CLOUDY;
    }
  }

  static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
