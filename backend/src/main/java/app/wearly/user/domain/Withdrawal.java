package app.wearly.user.domain;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 탈퇴 기록 (tb_user_withdrawal, doc/27). 신청하면 열리고, 취소하거나 회원을 지우면 닫힌다. 회원마다 열린 기록은 하나뿐이다. 상태를 바꾸는 메서드는
 * 새 값을 돌려준다.
 *
 * @param id 저장 전에는 null
 * @param reasonText 하고 싶은 말 (없으면 null)
 * @param purgeAfter 이 시각이 지나면 회원을 지운다
 * @param canceledBy 취소한 쪽 (취소하지 않았으면 null)
 * @param purgedBy 지운 쪽 (지우지 않았으면 null)
 */
public record Withdrawal(
    Long id,
    long userId,
    WithdrawalReason reason,
    String reasonText,
    Instant requestedAt,
    Instant purgeAfter,
    Instant canceledAt,
    Closer canceledBy,
    Instant purgedAt,
    Closer purgedBy) {

  public static final int REASON_TEXT_MAX = 200;

  /** 기록을 닫은 쪽 (tb_user_withdrawal.canceled_by · purged_by) */
  public enum Closer {
    /** 회원이 다시 로그인해 취소 */
    USER,
    /** 관리자 */
    ADMIN,
    /** 예약 작업 (삭제만) */
    SCHEDULER
  }

  /**
   * 새 탈퇴 신청. 사유는 꼭 고르고(정해진 값), 하고 싶은 말은 앞뒤 공백을 지워 200자까지(비면 null). 틀리면 reason·reasonText 필드 오류를 모아
   * DomainValidationException
   */
  public static Withdrawal request(
      long userId, String reason, String reasonText, Instant now, Duration grace) {
    List<FieldError> errors = new ArrayList<>();
    WithdrawalReason r = null;
    if (reason == null || reason.isBlank()) {
      errors.add(FieldError.required("reason"));
    } else {
      r = WithdrawalReason.parse(reason).orElse(null);
      if (r == null) {
        errors.add(FieldError.notAllowed("reason", WithdrawalReason.names()));
      }
    }
    String text = reasonText == null || reasonText.isBlank() ? null : reasonText.strip();
    if (text != null && text.length() > REASON_TEXT_MAX) {
      errors.add(FieldError.tooLong("reasonText", REASON_TEXT_MAX));
    }
    DomainValidationException.throwIfAny(errors);
    return new Withdrawal(null, userId, r, text, now, now.plus(grace), null, null, null, null);
  }

  /** 취소·삭제되지 않았다 */
  public boolean isOpen() {
    return canceledAt == null && purgedAt == null;
  }

  public Withdrawal cancel(Closer by, Instant now) {
    return new Withdrawal(
        id, userId, reason, reasonText, requestedAt, purgeAfter, now, by, null, null);
  }
}
