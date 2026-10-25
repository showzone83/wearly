package app.wearly.pairing.application.port.out.dto;

import java.time.Instant;

/** 연결 한 행과 양쪽 이름 (목록) */
public record PairingRowDto(
    long pairingId,
    long ownerId,
    String ownerName,
    long helperId,
    String helperName,
    String permission,
    String status,
    String issuedBy,
    boolean notifyOwner,
    Instant createdAt,
    Instant approvedAt) {}
