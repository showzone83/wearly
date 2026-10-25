package app.wearly.auth.adapter.out.notify;

import app.wearly.auth.application.port.out.PasswordNoticePort;
import app.wearly.auth.domain.PasswordReminderNotice;
import app.wearly.notify.application.port.in.PushNotifyUseCase;
import app.wearly.notify.application.port.in.dto.PushNoticeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** PasswordNoticePort 구현: notify 컨텍스트의 일반 알림으로 보낸다 */
@Component
class PasswordNoticeNotifyAdapter implements PasswordNoticePort {

  private static final Logger log = LoggerFactory.getLogger(PasswordNoticeNotifyAdapter.class);

  private final PushNotifyUseCase push;

  PasswordNoticeNotifyAdapter(PushNotifyUseCase push) {
    this.push = push;
  }

  @Override
  public boolean send(long userId, PasswordReminderNotice notice) {
    try {
      return push.notifyUser(
              userId,
              new PushNoticeDto(notice.title(), notice.body(), notice.data(), notice.channelId()))
          > 0;
    } catch (RuntimeException e) {
      log.warn("비밀번호 변경 알림 실패: user {}", userId, e);
      return false;
    }
  }
}
