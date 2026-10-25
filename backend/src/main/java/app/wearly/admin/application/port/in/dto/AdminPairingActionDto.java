package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/** 대리 작업 기록 한 줄. action = ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE · ITEM_RESTORE */
public record AdminPairingActionDto(
    Long id, String action, Long itemId, String itemName, Instant createdAt) {}
