package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.PasswordReminderRunDto;
import java.time.Instant;

/** 비밀번호 변경 알림 (28 문서 2단계). 예약 작업이 부르고, 테스트는 시각을 넣어 직접 부른다 */
public interface PasswordReminderUseCase {

  /** 지금 기한이 지난 회원에게 보낸다 */
  PasswordReminderRunDto remindDue();

  /**
   * now 기준으로 비밀번호 기한이 지났고 이번 주기에 아직 알리지 않은 활성 회원에게 푸시를 보낸다. 한 번에 정해진
   * 수(wearly.auth.password.reminder-batch)까지. 기능이 꺼졌으면(주기 0) 아무것도 하지 않는다
   */
  PasswordReminderRunDto remindDue(Instant now);
}
