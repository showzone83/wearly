package app.wearly.weather.application.port.out;

import app.wearly.weather.domain.ProviderHealth;
import app.wearly.weather.domain.WeatherProvider;
import java.util.List;
import java.util.Optional;

/** 날씨 공급자 (tb_weather_provider). 관리자 설정 칸과 실행 상태 칸을 따로 저장해서, 실행 중에 관리자가 설정을 바꿔도 서로 덮어쓰지 않는다. */
public interface WeatherProviderPort {

  /** priority 오름차순, 같으면 코드 순 */
  List<WeatherProvider> findAll();

  Optional<WeatherProvider> find(String code);

  /** enabled·priority·monthlyQuota·maxForecastDays·updatedBy·updatedAt만 저장 */
  WeatherProvider saveConfig(WeatherProvider provider);

  /** 상태 칸만 저장 */
  void saveHealth(String code, ProviderHealth health);
}
