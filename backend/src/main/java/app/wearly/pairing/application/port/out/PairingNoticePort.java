package app.wearly.pairing.application.port.out;

import app.wearly.pairing.domain.PairingNotices;

/** 회원에게 알림 보내기 (notify 컨텍스트의 푸시). 알림은 부가 기능이라 실패해도 던지지 않는다 */
public interface PairingNoticePort {

  /** 바로 보낸다 (외부 호출이므로 트랜잭션 밖에서). 공급자가 받은 기기가 하나라도 있으면 true */
  boolean send(long userId, long pairingId, PairingNotices notice);

  /** 지금 트랜잭션이 커밋된 뒤에 보낸다. 롤백되면 보내지 않는다. 트랜잭션이 없으면 바로 보낸다 */
  void sendAfterCommit(long userId, long pairingId, PairingNotices notice);
}
