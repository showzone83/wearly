package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminPairingDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * GET /admin/pairings 항목 (doc/26 6단계). status = PENDING · ACTIVE · ENDED, endedBy = OWNER · HELPER
 * · EXPIRED · ADMIN (끝나지 않았으면 null), permission = VIEW · EDIT (승인 전이면 null)
 */
public record AdminPairingResponse(
    String id,
    String status,
    String permission,
    String issuedBy,
    boolean notifyOwner,
    String createdAt,
    String approvedAt,
    String endedAt,
    String endedBy,
    String ownerId,
    String ownerEmail,
    String ownerName,
    String helperId,
    String helperEmail,
    String helperName,
    long actionCount,
    String lastActionAt) {

  public static AdminPairingResponse from(AdminPairingDto d) {
    return new AdminPairingResponse(
        Ids.format(d.id()),
        d.status(),
        d.permission(),
        d.issuedBy(),
        d.notifyOwner(),
        Times.iso(d.createdAt()),
        Times.iso(d.approvedAt()),
        Times.iso(d.endedAt()),
        d.endedBy(),
        Ids.format(d.ownerId()),
        d.ownerEmail(),
        d.ownerName(),
        Ids.format(d.helperId()),
        d.helperEmail(),
        d.helperName(),
        d.actionCount(),
        Times.iso(d.lastActionAt()));
  }
}
