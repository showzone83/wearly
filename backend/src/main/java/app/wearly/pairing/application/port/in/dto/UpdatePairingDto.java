package app.wearly.pairing.application.port.in.dto;

/**
 * 연결 바꾸기 (주인). null인 값은 그대로 둔다.
 *
 * @param permission VIEW · EDIT
 * @param notifyOwner 돕는 사람의 작업을 나에게 알릴지
 */
public record UpdatePairingDto(String permission, Boolean notifyOwner) {}
