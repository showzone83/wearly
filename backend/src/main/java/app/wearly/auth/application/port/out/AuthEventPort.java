package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.AuthEvent;

/** 로그인 이력 남기기 (tb_auth_event, JPA). 부른 쪽 트랜잭션에 함께 쓴다 */
public interface AuthEventPort {

  void record(AuthEvent event);
}
