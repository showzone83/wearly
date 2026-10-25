package app.wearly.pairing.application.port.in.dto;

/** 돕는 사람이 한 일 (tb_closet_proxy_action.action). 조회·AI 분석은 기록하지 않는다 */
public enum ProxyAction {
  /** 옷 등록 */
  ITEM_CREATE,
  /** 옷 수정 (사진 바꾸기 포함) */
  ITEM_UPDATE,
  /** 사용 중지 */
  ITEM_RETIRE,
  /** 다시 사용 */
  ITEM_RESTORE
}
