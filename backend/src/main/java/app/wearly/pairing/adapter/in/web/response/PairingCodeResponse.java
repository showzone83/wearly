package app.wearly.pairing.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.pairing.application.port.in.dto.PairingCodeDto;

/**
 * POST /pairings/codes 응답.
 *
 * @param code 정리한 8자 (앱이 XXXX-XXXX로 보인다)
 * @param role 코드를 만든 자리
 * @param expiresAt 코드가 사라지는 시각
 */
public record PairingCodeResponse(String code, String role, String expiresAt) {

  public static PairingCodeResponse from(PairingCodeDto d) {
    return new PairingCodeResponse(d.code(), d.role(), Times.iso(d.expiresAt()));
  }
}
