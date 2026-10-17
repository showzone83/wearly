package app.wearly.weather.config;

import app.wearly.common.config.WearlyProperties;
import app.wearly.weather.domain.WeatherOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 날씨 설정 (17 문서 5장): 서버 설정(wearly.weather.*)을 유스케이스가 쓰는 값으로 바꾸고 스케줄러를 켠다. 주기 실행 자체는
 * adapter.in.schedule.WeatherScheduler가 한다 (wearly.weather.scheduler-enabled=false면 만들지 않는다).
 */
@Configuration
@EnableScheduling
public class WeatherConfig {

  /** WEARLY_WEATHER_PROVIDER가 db·mock이 아니면 여기서 시작이 멈춘다 */
  @Bean
  WeatherOptions weatherOptions(WearlyProperties properties) {
    WearlyProperties.Weather w = properties.weather();
    return new WeatherOptions(
        WeatherOptions.isMock(w.provider()), w.schedulerEnabled(), w.logRetentionDays());
  }
}
