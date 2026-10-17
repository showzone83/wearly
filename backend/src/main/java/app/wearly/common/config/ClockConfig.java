package app.wearly.common.config;

import app.wearly.common.domain.Zones;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  /** 날짜 기준(일일 한도, 최근 착용)은 한국 시간이다. 값은 common.domain.Zones에 있다 */
  public static final ZoneId SEOUL = Zones.SEOUL;

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
