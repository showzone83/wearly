package app.wearly.pairing.application.port.in.dto;

import java.time.Instant;

/**
 * 연결 하나를 보는 회원 쪽에서 본 모양.
 *
 * @param role 보는 회원의 자리 (OWNER: 내 옷장을 돕는 사람 · HELPER: 내가 돕는 옷장)
 * @param partnerId 상대 회원
 * @param partnerName 상대 이름
 * @param permission 승인 전이면 null
 * @param status PENDING · ACTIVE
 * @param issuedBy 코드를 만든 쪽 (OWNER · HELPER)
 */
public record PairingDto(
    long id,
    String role,
    long partnerId,
    String partnerName,
    String permission,
    String status,
    String issuedBy,
    boolean notifyOwner,
    Instant createdAt,
    Instant approvedAt) {}
