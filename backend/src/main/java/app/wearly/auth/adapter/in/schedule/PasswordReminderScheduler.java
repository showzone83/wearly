package app.wearly.auth.adapter.in.schedule;

import app.wearly.auth.application.port.in.PasswordReminderUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 변경 알림 작업 (28 문서 2단계): wearly.auth.password.reminder-cron(한국 시간, 기본 매일 10:00)마다 비밀번호 기한이 지난
 * 회원에게 푸시를 보낸다. wearly.auth.password.reminder-enabled=false면 만들지 않는다 (테스트는 유스케이스를 직접 부른다). 예약은
 * user의 UserConfig가 켠다(@EnableScheduling).
 */
@Component
@ConditionalOnProperty(
    name = "wearly.auth.password.reminder-enabled",
    havingValue = "true",
    matchIfMissing = true)
class PasswordReminderScheduler {

  private static final Logger log = LoggerFactory.getLogger(PasswordReminderScheduler.class);

  private final PasswordReminderUseCase reminders;

  PasswordReminderScheduler(PasswordReminderUseCase reminders) {
    this.reminders = reminders;
  }

  @Scheduled(cron = "${wearly.auth.password.reminder-cron:0 0 10 * * *}", zone = "Asia/Seoul")
  void run() {
    try {
      reminders.remindDue();
    } catch (RuntimeException e) {
      log.error("비밀번호 변경 알림 작업 실패 (다음 예약 때 다시)", e);
    }
  }
}
