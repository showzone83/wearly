package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminPairingActionDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * GET /admin/pairings/{id}/actions 항목. action = ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE ·
 * ITEM_RESTORE
 */
public record AdminPairingActionResponse(
    String id, String action, String itemId, String itemName, String createdAt) {

  public static AdminPairingActionResponse from(AdminPairingActionDto d) {
    return new AdminPairingActionResponse(
        Ids.format(d.id()),
        d.action(),
        Ids.format(d.itemId()),
        d.itemName(),
        Times.iso(d.createdAt()));
  }
}
