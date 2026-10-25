package app.wearly.admin.application.port.in.dto;

import java.time.Instant;
import java.util.List;

/**
 * 탈퇴 요약 (doc/27 4단계): from 이후 신청한 탈퇴의 상태별·사유별 수와, 기간과 상관없이 기한이 지났는데 남은 탈퇴 예정 수(overdue).
 *
 * @param reasons 사유별 수 (많은 순, 신청이 없는 사유는 빠진다)
 */
public record AdminWithdrawalSummaryDto(
    int days,
    Instant from,
    long total,
    long open,
    long canceled,
    long purged,
    long overdue,
    List<ReasonCount> reasons) {

  public record ReasonCount(String reason, long count) {}
}
