package app.wearly.pairing.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.pairing.application.port.in.dto.ProxyActionDto;

/**
 * GET /pairings/{id}/actions 항목: 돕는 사람이 한 일.
 *
 * @param action ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE · ITEM_RESTORE
 * @param itemId 옷 ID (주인이 완전 삭제했으면 지금은 없을 수 있다)
 * @param itemName 기록 당시 옷 이름
 */
public record ProxyActionResponse(
    String id, String action, String itemId, String itemName, String createdAt) {

  public static ProxyActionResponse from(ProxyActionDto d) {
    return new ProxyActionResponse(
        Ids.format(d.id()),
        d.action(),
        Ids.format(d.itemId()),
        d.itemName(),
        Times.iso(d.createdAt()));
  }
}
