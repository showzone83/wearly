package app.wearly.pairing.adapter.in.web.request;

import app.wearly.pairing.application.port.in.dto.UpdatePairingDto;

/**
 * PATCH /pairings/{id} 본문 (주인). 없는 값은 그대로 둔다.
 *
 * @param permission VIEW · EDIT (연결된 뒤에만)
 * @param notifyOwner 돕는 사람의 작업을 나에게 알릴지
 */
public record UpdatePairingRequest(String permission, Boolean notifyOwner) {

  public UpdatePairingDto toDto() {
    return new UpdatePairingDto(permission, notifyOwner);
  }
}
