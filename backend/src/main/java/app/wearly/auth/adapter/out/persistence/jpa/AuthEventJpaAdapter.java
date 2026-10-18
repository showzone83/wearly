package app.wearly.auth.adapter.out.persistence.jpa;

import app.wearly.auth.application.port.out.AuthEventPort;
import app.wearly.auth.domain.AuthEvent;
import org.springframework.stereotype.Component;

/** AuthEventPort 구현 (JPA 네이티브 INSERT) */
@Component
class AuthEventJpaAdapter implements AuthEventPort {

  private final AuthEventJpaRepository events;

  AuthEventJpaAdapter(AuthEventJpaRepository events) {
    this.events = events;
  }

  @Override
  public void record(AuthEvent e) {
    events.insertIfUserExists(
        e.userId(),
        e.type().name(),
        e.sessionId(),
        e.client() == null ? null : e.client().name(),
        e.ip(),
        e.userAgent(),
        e.createdAt(),
        e.ssoProvider(),
        e.ssoAmr());
  }
}
