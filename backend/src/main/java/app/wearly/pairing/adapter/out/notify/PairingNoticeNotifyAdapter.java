package app.wearly.pairing.adapter.out.notify;

import app.wearly.notify.application.port.in.PushNotifyUseCase;
import app.wearly.notify.application.port.in.dto.PushNoticeDto;
import app.wearly.pairing.application.port.out.PairingNoticePort;
import app.wearly.pairing.domain.PairingNotices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PairingNoticePort 구현: notify 컨텍스트의 일반 알림으로 보낸다. 커밋 뒤 보내기는 트랜잭션 동기화에 올린다 */
@Component
class PairingNoticeNotifyAdapter implements PairingNoticePort {

  private static final Logger log = LoggerFactory.getLogger(PairingNoticeNotifyAdapter.class);

  private final PushNotifyUseCase push;

  PairingNoticeNotifyAdapter(PushNotifyUseCase push) {
    this.push = push;
  }

  @Override
  public boolean send(long userId, long pairingId, PairingNotices notice) {
    try {
      return push.notifyUser(
              userId,
              new PushNoticeDto(
                  notice.title(), notice.body(), notice.data(pairingId), PairingNotices.CHANNEL_ID))
          > 0;
    } catch (RuntimeException e) {
      log.warn("옷장 도우미 알림 실패: user {} {}", userId, notice.type(), e);
      return false;
    }
  }

  @Override
  public void sendAfterCommit(long userId, long pairingId, PairingNotices notice) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      send(userId, pairingId, notice);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            send(userId, pairingId, notice);
          }
        });
  }
}
