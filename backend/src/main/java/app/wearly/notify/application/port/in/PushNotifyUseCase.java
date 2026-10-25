package app.wearly.notify.application.port.in;

import app.wearly.notify.application.port.in.dto.PushNoticeDto;

/** 다른 컨텍스트가 회원에게 알림을 보낸다 (예: 옷장 도우미 doc/26). 푸시 토큰 조회·전송·죽은 토큰 정리는 notify가 한다 */
public interface PushNotifyUseCase {

  /**
   * 이 회원의 모든 기기로 보낸다. 서버 푸시가 꺼져 있거나 토큰이 없으면 보내지 않는다. 실패해도 던지지 않는다 (알림은 부가 기능이다).
   *
   * @return 공급자가 받은 기기 수
   */
  int notifyUser(long userId, PushNoticeDto notice);
}
