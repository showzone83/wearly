package app.wearly.auth.adapter.in.user;

import app.wearly.auth.application.port.in.SessionUseCase;
import app.wearly.user.application.port.in.UserWithdrawnListener;
import org.springframework.stereotype.Component;

/**
 * user 컨텍스트의 탈퇴 신청 알림(UserWithdrawnListener, doc/27)을 받아 그 회원의 로그인 세션(Valkey)을 모두 끊는다. 남은 Access
 * 토큰은 세션 확인에서 걸러져 401 TOKEN_INVALID가 되고, 다시 로그인하면 탈퇴 예정 안내(403 ACCOUNT_WITHDRAWN)를 받는다.
 */
@Component
class SessionCleanupOnUserWithdrawn implements UserWithdrawnListener {

  private final SessionUseCase sessions;

  SessionCleanupOnUserWithdrawn(SessionUseCase sessions) {
    this.sessions = sessions;
  }

  @Override
  public void userWithdrawn(long userId) {
    sessions.dropUserSessions(userId);
  }
}
