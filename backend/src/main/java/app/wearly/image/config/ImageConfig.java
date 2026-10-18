package app.wearly.image.config;

import app.wearly.common.config.WearlyProperties;
import app.wearly.image.domain.ImageSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** wearly.storage.* → 사진 유스케이스 설정 값 (유스케이스는 Spring 설정 클래스를 직접 보지 않는다). 사진 정리 주기 작업을 위해 스케줄링을 켠다 */
@Configuration
@EnableScheduling
public class ImageConfig {

  @Bean
  ImageSettings imageSettings(WearlyProperties properties) {
    WearlyProperties.Storage s = properties.storage();
    return new ImageSettings(
        s.uploadUrlTtl(),
        s.downloadUrlTtl(),
        s.maxUploadBytes().toBytes(),
        s.dailyUploadLimit(),
        s.staleAfter());
  }
}
