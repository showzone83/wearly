package app.wearly.auth.adapter.in.user;

import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.user.application.port.in.UserDeletedListener;
import org.springframework.stereotype.Component;

/**
 * user 컨텍스트가 정의한 탈퇴 알림(UserDeletedListener)을 받아 그 회원의 로그인 세션(Valkey)을 지운다. 회원 행과 로그인 이력은 이미 지워지므로
 * 이력은 남기지 않는다. 의존은 auth → user 한 방향이다.
 */
@Component
class SessionCleanupOnUserDeleted implements UserDeletedListener {

  private final SessionUseCase sessions;

  SessionCleanupOnUserDeleted(SessionUseCase sessions) {
    this.sessions = sessions;
  }

  @Override
  public void userDeleted(long userId) {
    sessions.dropUserSessions(userId);
  }
}
