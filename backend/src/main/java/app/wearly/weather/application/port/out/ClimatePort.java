package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.MonthlyClimate;
import java.util.List;
import java.util.Optional;

/** 격자별 월 평년값 (tb_weather_climate) */
public interface ClimatePort {

  Optional<MonthlyClimate> find(String gridKey, int month);

  /** 한 격자의 달들을 한 번에 바꾼다 (없는 달은 새로 만든다) */
  void saveAll(List<MonthlyClimate> months);
}
