package app.wearly.notify.adapter.in.user;

import app.wearly.notify.application.port.in.PushTokenUseCase;
import app.wearly.user.application.port.in.UserWithdrawnListener;
import org.springframework.stereotype.Component;

/**
 * user 컨텍스트의 탈퇴 신청 알림(doc/27)을 받아 그 회원의 푸시 토큰을 모든 기기에서 지운다. 탈퇴 예정 회원에게는 D-1·옷장 도우미 알림이 가지 않는다. 탈퇴를
 * 취소하면 앱이 토큰을 다시 등록한다. 의존은 notify → user 한 방향이다.
 */
@Component
class PushTokenCleanupOnUserWithdrawn implements UserWithdrawnListener {

  private final PushTokenUseCase tokens;

  PushTokenCleanupOnUserWithdrawn(PushTokenUseCase tokens) {
    this.tokens = tokens;
  }

  @Override
  public void userWithdrawn(long userId) {
    tokens.unregisterAll(userId);
  }
}
