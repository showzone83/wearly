package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.GridKey;
import app.wearly.weather.domain.PlaceSnapshot;
import java.time.Instant;
import java.util.Optional;

/** REGION 밖 장소(격자)의 최신 예보 (tb_weather_place_snapshot) */
public interface PlaceSnapshotPort {

  Optional<PlaceSnapshot> find(String gridKey);

  /** 받은 값으로 바꾼다 (행이 없으면 만든다). 이전 오류는 비운다 */
  void save(PlaceSnapshot snapshot);

  /** 받기 실패: 이전 값은 두고 오류만 남긴다 (행이 없으면 오류만 든 행을 만든다) */
  void recordError(GridKey grid, String error, Instant at);
}
