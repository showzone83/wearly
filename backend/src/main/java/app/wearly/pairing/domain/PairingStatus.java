package app.wearly.pairing.domain;

/** 연결 상태 (tb_closet_pairing.status) */
public enum PairingStatus {
  /** 돕는 사람이 주인 코드를 입력해 주인 승인을 기다린다 */
  PENDING,
  /** 연결됨 */
  ACTIVE,
  /** 끝남: 해제·거절·대기 만료. 작업 기록이 가리키므로 지우지 않는다 */
  ENDED
}
