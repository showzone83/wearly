package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.WeatherSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 지역별 최신 날씨 (tb_weather_snapshot) */
public interface WeatherSnapshotPort {

  Optional<WeatherSnapshot> find(String regionCode);

  List<WeatherSnapshot> findAll();

  /** 받은 값으로 바꾼다 (행이 없으면 만든다). 이전 오류는 비운다 */
  void save(WeatherSnapshot snapshot);

  /** 받기 실패: 이전 값은 두고 오류만 남긴다 (행이 없으면 오류만 든 행을 만든다) */
  void recordError(String regionCode, String error, Instant at);

  /** 가장 최근에 받은 시각 (서버 시작 실행 판단용) */
  Optional<Instant> latestFetchedAt();
}
