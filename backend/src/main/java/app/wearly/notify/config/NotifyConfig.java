package app.wearly.notify.config;

import app.wearly.notify.adapter.out.push.ExpoPushSenderAdapter;
import app.wearly.notify.adapter.out.push.LogPushSenderAdapter;
import app.wearly.notify.application.port.out.PushSenderPort;
import app.wearly.notify.domain.PushSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 설정: 서버 설정(wearly.notify.*)을 유스케이스가 쓰는 값으로 바꾸고 푸시 공급자(expo·log)를 고른다. D-1 작업 예약은
 * adapter.in.schedule.DayBeforeScheduler가 한다.
 */
@Configuration
@EnableScheduling
public class NotifyConfig {

  @Bean
  PushSettings pushSettings(NotifyProperties properties) {
    NotifyProperties.Push p = properties.push();
    return new PushSettings(
        p.enabled(),
        p.provider().equals("expo") ? PushSettings.Provider.EXPO : PushSettings.Provider.LOG,
        properties.dayBefore().time());
  }

  @Bean
  PushSenderPort pushSender(NotifyProperties properties, JsonMapper jsonMapper) {
    NotifyProperties.Push p = properties.push();
    if (p.provider().equals("expo")) {
      return new ExpoPushSenderAdapter(
          p.expo().baseUrl(), p.expo().accessToken(), p.expo().timeout(), jsonMapper);
    }
    return new LogPushSenderAdapter();
  }
}
