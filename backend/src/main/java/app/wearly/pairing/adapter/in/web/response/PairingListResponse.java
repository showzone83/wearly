package app.wearly.pairing.adapter.in.web.response;

import app.wearly.pairing.application.port.in.dto.PairingListDto;
import java.util.List;

/**
 * GET /pairings 응답 (최근 순).
 *
 * @param asOwner 내 옷장을 돕는 사람
 * @param asHelper 내가 돕는 옷장
 */
public record PairingListResponse(List<PairingResponse> asOwner, List<PairingResponse> asHelper) {

  public static PairingListResponse from(PairingListDto d) {
    return new PairingListResponse(
        d.asOwner().stream().map(PairingResponse::from).toList(),
        d.asHelper().stream().map(PairingResponse::from).toList());
  }
}
