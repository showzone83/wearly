package app.wearly.pairing.application.port.out;

import java.time.Instant;

/** 대리 작업 기록 쓰기 (tb_closet_proxy_action, JPA) */
public interface ProxyActionPort {

  /**
   * @param action ITEM_CREATE · ITEM_UPDATE · ITEM_RETIRE · ITEM_RESTORE
   * @param itemName 기록 당시 옷 이름 (40자까지)
   */
  void record(
      long pairingId,
      long ownerId,
      long helperId,
      String action,
      long itemId,
      String itemName,
      Instant now);
}
