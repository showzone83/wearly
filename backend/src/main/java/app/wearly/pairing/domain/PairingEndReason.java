package app.wearly.pairing.domain;

/** 연결이 끝난 이유 (tb_closet_pairing.ended_by) */
public enum PairingEndReason {
  /** 주인이 해제하거나 대기 요청을 거절했다 */
  OWNER,
  /** 돕는 사람이 해제하거나 대기 요청을 취소했다 */
  HELPER,
  /** 승인을 기다리다 시간이 지났다 */
  EXPIRED,
  /** 관리자가 강제로 해제했다 (doc/26 6단계) */
  ADMIN;

  public static PairingEndReason by(PairingRole role) {
    return role == PairingRole.OWNER ? OWNER : HELPER;
  }
}
