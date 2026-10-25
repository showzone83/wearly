package app.wearly.user.config;

import app.wearly.user.domain.WithdrawalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 회원 설정: 서버 설정(wearly.user.*)을 유스케이스가 쓰는 값으로 바꾼다. 탈퇴 예약 삭제(doc/27 2단계)를 위해 예약 작업을 켠다 */
@Configuration
@EnableScheduling
public class UserConfig {

  @Bean
  WithdrawalPolicy withdrawalPolicy(WithdrawalProperties p) {
    return new WithdrawalPolicy(p.grace(), p.purgeBatch());
  }
}
