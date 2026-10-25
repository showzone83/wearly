package app.wearly.admin.application.port.out.dto;

import java.time.Instant;

/** 옷장 도우미 연결 한 행 (주인·돕는 사람 이메일·이름, 작업 수 포함) */
public record AdminPairingRowDto(
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
