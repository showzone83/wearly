package app.wearly.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 비밀번호 변경 주기 (wearly.auth.password.*, 28 문서). auth/config가 서버 설정에서 만든다. 기한은 비밀번호를 정한 시각과 마지막 연장 시각
 * 중 늦은 쪽에서 주기만큼 뒤다.
 *
 * @param interval 비밀번호 변경 주기 (기본 90일). 0이면 기능을 끈다
 * @param reminderBatch 변경 알림 예약 작업 한 번에 보내는 최대 회원 수
 */
public record PasswordChangePolicy(Duration interval, int reminderBatch) {

  public PasswordChangePolicy {
    if (interval == null || interval.isNegative()) {
      throw new IllegalArgumentException(
          "wearly.auth.password.change-interval은 0 이상이어야 해요: " + interval);
    }
    if (reminderBatch < 1) {
      throw new IllegalArgumentException(
          "wearly.auth.password.reminder-batch는 1 이상이어야 해요: " + reminderBatch);
    }
  }

  public boolean enabled() {
    return !interval.isZero();
  }

  /** 주기 일수 (앱 문구 "90일 연장"용, 하루 미만은 버림) */
  public long intervalDays() {
    return interval.toDays();
  }

  /** 바꿔야 하는 시각. 기능이 꺼졌거나 정한 시각을 모르면 null */
  public Instant dueAt(Instant changedAt, Instant postponedAt) {
    if (!enabled() || changedAt == null) {
      return null;
    }
    Instant base = postponedAt != null && postponedAt.isAfter(changedAt) ? postponedAt : changedAt;
    return base.plus(interval);
  }

  /** 기한이 지났는지 (기한 시각 포함) */
  public boolean isDue(Instant changedAt, Instant postponedAt, Instant now) {
    Instant due = dueAt(changedAt, postponedAt);
    return due != null && !now.isBefore(due);
  }
}
