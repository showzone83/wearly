package app.wearly.admin.application.port.in.dto;

import java.time.Instant;

/**
 * 옷장 도우미 연결 한 줄 (유스케이스 출력). status = PENDING · ACTIVE · ENDED, endedBy = OWNER · HELPER · EXPIRED ·
 * ADMIN
 */
public record AdminPairingDto(
    Long id,
    String status,
    String permission,
    String issuedBy,
    boolean notifyOwner,
    Instant createdAt,
    Instant approvedAt,
    Instant endedAt,
    String endedBy,
    Long ownerId,
    String ownerEmail,
    String ownerName,
    Long helperId,
    String helperEmail,
    String helperName,
    long actionCount,
    Instant lastActionAt) {}
