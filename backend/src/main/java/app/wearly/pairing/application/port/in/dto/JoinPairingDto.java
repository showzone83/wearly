package app.wearly.pairing.application.port.in.dto;

/**
 * 연결 코드 입력.
 *
 * @param code 입력한 코드 (대소문자·공백·하이픈 무시)
 * @param permission 돕는 사람 코드를 주인이 입력할 때 고른 권한 (VIEW · EDIT). 주인 코드면 쓰지 않는다
 */
public record JoinPairingDto(String code, String permission) {}
