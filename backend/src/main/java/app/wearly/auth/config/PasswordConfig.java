package app.wearly.auth.config;

import app.wearly.auth.domain.PasswordChangePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 비밀번호 변경 주기 (28 문서): 서버 설정(wearly.auth.password.*)을 유스케이스가 쓰는 값으로 바꾼다 */
@Configuration
public class PasswordConfig {

  @Bean
  PasswordChangePolicy passwordChangePolicy(PasswordProperties p) {
    return new PasswordChangePolicy(p.changeInterval(), p.reminderBatch());
  }
}
