package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminWithdrawalSummaryDto;
import app.wearly.common.web.Times;
import java.util.List;

/** GET /admin/withdrawals/summary (doc/27 4단계) */
public record AdminWithdrawalSummaryResponse(
    int days,
    String from,
    long total,
    long open,
    long canceled,
    long purged,
    long overdue,
    List<Reason> reasons) {

  public record Reason(String reason, long count) {}

  public static AdminWithdrawalSummaryResponse from(AdminWithdrawalSummaryDto d) {
    return new AdminWithdrawalSummaryResponse(
        d.days(),
        Times.iso(d.from()),
        d.total(),
        d.open(),
        d.canceled(),
        d.purged(),
        d.overdue(),
        d.reasons().stream().map(r -> new Reason(r.reason(), r.count())).toList());
  }
}
