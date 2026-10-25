package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminWithdrawalDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * GET /admin/withdrawals 항목 (doc/27 4단계). state = OPEN · CANCELED · PURGED, overdue = 탈퇴 예정인데 삭제 예정
 * 시각이 지남. email·name은 회원이 지워졌으면 null. canceledBy = USER · ADMIN, purgedBy = SCHEDULER · ADMIN
 */
public record AdminWithdrawalResponse(
    String id,
    String userId,
    String email,
    String name,
    String reason,
    String reasonText,
    String state,
    boolean overdue,
    String requestedAt,
    String purgeAt,
    String canceledAt,
    String canceledBy,
    String purgedAt,
    String purgedBy) {

  public static AdminWithdrawalResponse from(AdminWithdrawalDto d) {
    return new AdminWithdrawalResponse(
        Ids.format(d.id()),
        Ids.format(d.userId()),
        d.email(),
        d.name(),
        d.reason(),
        d.reasonText(),
        d.state(),
        d.overdue(),
        Times.iso(d.requestedAt()),
        Times.iso(d.purgeAt()),
        Times.iso(d.canceledAt()),
        d.canceledBy(),
        Times.iso(d.purgedAt()),
        d.purgedBy());
  }
}
