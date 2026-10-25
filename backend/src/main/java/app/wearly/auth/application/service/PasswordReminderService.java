package app.wearly.auth.application.service;

import app.wearly.auth.application.port.in.PasswordReminderUseCase;
import app.wearly.auth.application.port.in.dto.PasswordReminderRunDto;
import app.wearly.auth.application.port.out.CredentialPort;
import app.wearly.auth.application.port.out.PasswordNoticePort;
import app.wearly.auth.domain.PasswordChangePolicy;
import app.wearly.auth.domain.PasswordReminderNotice;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 변경 알림 (28 문서 2단계): 기한이 지난 회원마다 처리권(password_reminded_at)을 먼저 잡고 푸시를 보낸다. 잡은 뒤에는 푸시가 실패해도 이
 * 주기에는 다시 보내지 않는다(앱을 열면 앱이 따로 알린다). 한 명이 실패해도 나머지는 계속하고, 로그에는 건수만 남긴다
 */
@Service
public class PasswordReminderService implements PasswordReminderUseCase {

  private static final Logger log = LoggerFactory.getLogger(PasswordReminderService.class);

  private final CredentialPort credentials;
  private final PasswordNoticePort notices;
  private final PasswordChangePolicy policy;
  private final Clock clock;

  public PasswordReminderService(
      CredentialPort credentials,
      PasswordNoticePort notices,
      PasswordChangePolicy policy,
      Clock clock) {
    this.credentials = credentials;
    this.notices = notices;
    this.policy = policy;
    this.clock = clock;
  }

  @Override
  public PasswordReminderRunDto remindDue() {
    return remindDue(clock.instant());
  }

  @Override
  public PasswordReminderRunDto remindDue(Instant now) {
    if (!policy.enabled()) {
      return new PasswordReminderRunDto(0, 0, 0, 0, 0);
    }
    Instant dueBy = now.minus(policy.interval());
    List<Long> due = credentials.findReminderDueUserIds(dueBy, policy.reminderBatch());
    PasswordReminderNotice notice = PasswordReminderNotice.of(policy.intervalDays());
    int reminded = 0;
    int delivered = 0;
    int skipped = 0;
    int failed = 0;
    for (long userId : due) {
      try {
        if (!credentials.claimReminder(userId, dueBy, now)) {
          skipped++;
          continue;
        }
        reminded++;
        if (notices.send(userId, notice)) {
          delivered++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("비밀번호 변경 알림 실패 (다음에 다시): user {}", userId, e);
      }
    }
    if (!due.isEmpty()) {
      log.info(
          "비밀번호 변경 알림 ({} 기준): 대상 {}, 알림 {}, 기기 도착 {}, 건너뜀 {}, 실패 {}",
          now,
          due.size(),
          reminded,
          delivered,
          skipped,
          failed);
    }
    return new PasswordReminderRunDto(due.size(), reminded, delivered, skipped, failed);
  }
}
