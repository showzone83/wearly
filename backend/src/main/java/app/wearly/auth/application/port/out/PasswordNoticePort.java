package app.wearly.auth.application.port.out;

import app.wearly.auth.domain.PasswordReminderNotice;

/** 비밀번호 변경 알림 보내기 (notify 컨텍스트, 28 문서 2단계) */
public interface PasswordNoticePort {

  /**
   * 그 회원의 모든 기기로 보낸다. 실패해도 던지지 않는다 (알림은 부가 기능이다).
   *
   * @return 공급자가 한 기기라도 받았는지 (푸시가 꺼졌거나 토큰이 없으면 false)
   */
  boolean send(long userId, PasswordReminderNotice notice);
}
