package app.wearly.pairing.application.port.in.dto;

/**
 * 연결 코드 입력.
 *
 * @param code 입력한 코드 (대소문자·공백·하이픈 무시)
 */
public record JoinPairingDto(String code) {}
