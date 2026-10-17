package app.wearly.common.config;

import app.wearly.ai.domain.AiRunMode;
import app.wearly.ai.domain.AiSettings;
import app.wearly.auth.domain.TokenPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 환경변수(wearly.*)를 유스케이스가 쓰는 설정 값으로 바꾼다. 유스케이스는 Spring 설정 클래스를 직접 보지 않는다 */
@Configuration
public class ApplicationSettingsConfig {

  @Bean
  TokenPolicy tokenPolicy(WearlyProperties properties) {
    return new TokenPolicy(properties.jwt().refreshTtl());
  }

  /** WEARLY_AI_PROVIDER가 틀리면 여기서 시작이 멈춘다 (AiProviderFailureAnalyzer가 안내) */
  @Bean
  AiSettings aiSettings(WearlyProperties properties) {
    WearlyProperties.Ai ai = properties.ai();
    return new AiSettings(AiRunMode.isMock(ai.provider()), ai.dailyLimit(), ai.allowBaseUrl());
  }
}
