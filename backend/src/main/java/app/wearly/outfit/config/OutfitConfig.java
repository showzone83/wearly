package app.wearly.outfit.config;

import app.wearly.common.config.WearlyProperties;
import app.wearly.outfit.domain.OutfitSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 환경변수(wearly.ai.outfit-daily-limit)를 outfit 유스케이스가 쓰는 설정 값으로 바꾼다 */
@Configuration
public class OutfitConfig {

  @Bean
  OutfitSettings outfitSettings(WearlyProperties properties) {
    return new OutfitSettings(properties.ai().outfitDailyLimit());
  }
}
