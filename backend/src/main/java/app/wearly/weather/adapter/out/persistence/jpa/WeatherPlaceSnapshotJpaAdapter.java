package app.wearly.weather.adapter.out.persistence.jpa;

import app.wearly.weather.application.port.out.PlaceSnapshotPort;
import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.PlaceSnapshot;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PlaceSnapshotPort 구현 (JPA). current·days JSONB 모양은 WeatherSnapshotJpaAdapter와 같다 */
@Component
class WeatherPlaceSnapshotJpaAdapter implements PlaceSnapshotPort {

  private final WeatherPlaceSnapshotJpaRepository places;

  WeatherPlaceSnapshotJpaAdapter(WeatherPlaceSnapshotJpaRepository places) {
    this.places = places;
  }

  @Override
  public Optional<PlaceSnapshot> find(String gridKey) {
    return gridKey == null
        ? Optional.empty()
        : places.findById(gridKey).map(WeatherPlaceSnapshotJpaAdapter::toDomain);
  }

  @Override
  @Transactional
  public void save(PlaceSnapshot s) {
    WeatherPlaceSnapshotEntity e =
        places
            .findById(s.gridKey())
            .orElseGet(() -> new WeatherPlaceSnapshotEntity(s.gridKey(), s.lat(), s.lon()));
    e.fill(
        WeatherSnapshotJpaAdapter.truncate(s.label(), 100),
        s.providerCode(),
        WeatherSnapshotJpaAdapter.current(s.current()),
        s.days().stream().map(WeatherSnapshotJpaAdapter::day).toList(),
        s.fetchedAt());
    places.saveAndFlush(e);
  }

  @Override
  @Transactional
  public void recordError(GridKey grid, String error, Instant at) {
    WeatherPlaceSnapshotEntity e =
        places
            .findById(grid.key())
            .orElseGet(() -> new WeatherPlaceSnapshotEntity(grid.key(), grid.lat(), grid.lon()));
    e.error(WeatherSnapshotJpaAdapter.truncate(error, WeatherSnapshotJpaAdapter.MAX_ERROR), at);
    places.saveAndFlush(e);
  }

  private static PlaceSnapshot toDomain(WeatherPlaceSnapshotEntity e) {
    return new PlaceSnapshot(
        e.getGridKey(),
        e.getLat(),
        e.getLon(),
        e.getLabel(),
        e.getProviderCode(),
        WeatherSnapshotJpaAdapter.currentOf(e.getCurrent()),
        WeatherSnapshotJpaAdapter.daysOf(e.getDays()),
        e.getFetchedAt(),
        e.getLastError(),
        e.getLastErrorAt());
  }
}
