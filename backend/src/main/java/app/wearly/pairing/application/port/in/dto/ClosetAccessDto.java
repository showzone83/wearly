package app.wearly.pairing.application.port.in.dto;

/**
 * 확인을 지난 대리 접근.
 *
 * @param pairingId 연결
 * @param ownerId 옷장 주인 (데이터를 찾고 저장할 회원)
 * @param helperId 돕는 사람 (작업한 회원)
 */
public record ClosetAccessDto(long pairingId, long ownerId, long helperId) {}
