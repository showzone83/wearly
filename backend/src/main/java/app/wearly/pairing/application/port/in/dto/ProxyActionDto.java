package app.wearly.pairing.application.port.in.dto;

import java.time.Instant;

/**
 * 대리 작업 기록 한 건.
 *
 * @param action ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE · ITEM_RESTORE
 * @param itemId 옷 (주인이 완전 삭제했으면 지금은 없을 수 있다)
 * @param itemName 기록 당시 옷 이름
 */
public record ProxyActionDto(
    long id, String action, long itemId, String itemName, Instant createdAt) {}
