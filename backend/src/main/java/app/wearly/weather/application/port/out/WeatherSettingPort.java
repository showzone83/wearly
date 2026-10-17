package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.WeatherSetting;
import java.util.Optional;

/** 스케줄 설정 한 행 (tb_weather_setting id = 1) */
public interface WeatherSettingPort {

  Optional<WeatherSetting> load();

  WeatherSetting save(WeatherSetting setting);
}
