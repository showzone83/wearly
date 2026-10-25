package app.wearly.user.config;

import app.wearly.user.domain.WithdrawalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 회원 설정: 서버 설정(wearly.user.*)을 유스케이스가 쓰는 값으로 바꾼다 */
@Configuration
public class UserConfig {

  @Bean
  WithdrawalPolicy withdrawalPolicy(WithdrawalProperties p) {
    return new WithdrawalPolicy(p.grace());
  }
}
