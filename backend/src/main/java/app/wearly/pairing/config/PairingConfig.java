package app.wearly.pairing.config;

import app.wearly.pairing.domain.PairingSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 옷장 도우미 설정: 서버 설정(wearly.pairing.*)을 유스케이스가 쓰는 값으로 바꾼다 */
@Configuration
public class PairingConfig {

  @Bean
  PairingSettings pairingSettings(PairingProperties p) {
    return new PairingSettings(
        p.maxHelpersPerOwner(),
        p.maxClosetsPerHelper(),
        p.codeTtl(),
        p.pendingTtl(),
        p.joinAttemptLimit(),
        p.joinAttemptWindow(),
        p.notification().digestDelay(),
        p.notification().digestMaxWait());
  }
}
