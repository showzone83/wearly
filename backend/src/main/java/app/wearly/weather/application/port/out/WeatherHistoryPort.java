package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.WeatherHistory;
import java.time.LocalDate;
import java.util.Optional;

/** 지역·날짜별 날씨 이력 (tb_weather_daily_history). 목록 조회는 WeatherLogQueryPort */
public interface WeatherHistoryPort {

  Optional<WeatherHistory> find(String regionCode, LocalDate date);

  /** 행이 없으면 만들고 있으면 바꾼다 */
  void save(WeatherHistory history);
}
