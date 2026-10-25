package app.wearly.pairing.application.port.out.dto;

/** 묶음 알림에 넣을 작업 한 건 (action은 ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE · ITEM_RESTORE) */
public record ClaimedActionRowDto(String action, long itemId) {}
