package app.wearly.pairing.adapter.in.web.request;

import app.wearly.pairing.application.port.in.dto.JoinPairingDto;

/**
 * POST /pairings/join 본문. 검증은 유스케이스가 한다.
 *
 * @param code 연결 코드 (대소문자·공백·하이픈 무시)
 */
public record JoinPairingRequest(String code) {

  public JoinPairingDto toDto() {
    return new JoinPairingDto(code);
  }
}
