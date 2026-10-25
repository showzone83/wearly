package app.wearly.pairing.adapter.in.user;

import app.wearly.pairing.application.port.in.PairingUseCase;
import app.wearly.user.application.port.in.UserWithdrawnListener;
import org.springframework.stereotype.Component;

/**
 * user 컨텍스트의 탈퇴 신청 알림(doc/27)을 받아 그 회원의 옷장 도우미 연결을 모두 끝내고 연결 코드를 지운다. 돕는 사람이 탈퇴 예정인 주인의 옷장을 계속 보지
 * 않게, 탈퇴 예정인 돕는 사람이 남의 옷장을 계속 보지 않게 한다. 의존은 pairing → user 한 방향이다.
 */
@Component
class PairingCleanupOnUserWithdrawn implements UserWithdrawnListener {

  private final PairingUseCase pairings;

  PairingCleanupOnUserWithdrawn(PairingUseCase pairings) {
    this.pairings = pairings;
  }

  @Override
  public void userWithdrawn(long userId) {
    pairings.endAllOnWithdrawal(userId);
  }
}
