package app.wearly.pairing.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.pairing.application.port.in.dto.PairingDto;

/**
 * 연결 하나 (요청한 회원 쪽에서 본 모양).
 *
 * @param role 내 자리: OWNER(내 옷장을 돕는 사람) · HELPER(내가 돕는 옷장)
 * @param partnerId 상대 회원 ID
 * @param partnerName 상대 이름
 * @param permission VIEW · EDIT, 승인 전이면 null
 * @param status PENDING(주인 승인 대기) · ACTIVE
 * @param issuedBy 코드를 만든 쪽 (OWNER · HELPER)
 * @param notifyOwner 돕는 사람의 작업을 주인에게 알릴지
 * @param approvedAt 승인 시각, 승인 전이면 null
 */
public record PairingResponse(
    String id,
    String role,
    String partnerId,
    String partnerName,
    String permission,
    String status,
    String issuedBy,
    boolean notifyOwner,
    String createdAt,
    String approvedAt) {

  public static PairingResponse from(PairingDto d) {
    return new PairingResponse(
        Ids.format(d.id()),
        d.role(),
        Ids.format(d.partnerId()),
        d.partnerName(),
        d.permission(),
        d.status(),
        d.issuedBy(),
        d.notifyOwner(),
        Times.iso(d.createdAt()),
        Times.iso(d.approvedAt()));
  }
}
