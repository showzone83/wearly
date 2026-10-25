package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 대리 작업 기록 한 행 */
public record AdminPairingActionRowDto(
    Long id, String action, Long itemId, String itemName, Instant createdAt) {}
