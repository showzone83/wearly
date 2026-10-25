package app.wearly.pairing.application.port.in.dto;

/**
 * 강제로 끝낸 연결의 끝내기 전 모습 (관리자 변경 이력용).
 *
 * @param permission 승인 전이면 null
 * @param status 끝내기 전 상태 (PENDING · ACTIVE)
 */
public record PairingAdminEndDto(
    long id, long ownerId, long helperId, String permission, String status) {}
