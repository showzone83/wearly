package app.wearly.pairing.application.port.out.dto;

import java.time.Instant;

/** 대리 작업 기록 한 행 (목록) */
public record ProxyActionRowDto(
    long actionId, String action, long itemId, String itemName, Instant createdAt) {}
