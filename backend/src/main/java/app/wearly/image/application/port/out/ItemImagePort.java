package app.wearly.image.application.port.out;

import app.wearly.image.domain.ItemImage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 옷 사진 메타데이터 쓰기·단건 조회 (JPA) */
public interface ItemImagePort {

  /** 남의 이미지는 찾지 못한 것으로 본다 */
  Optional<ItemImage> find(long userId, UUID imageId);

  /** find와 같고 행을 잠근다 (트랜잭션 안에서, 같은 이미지를 두 아이템에 동시에 붙이지 않게) */
  Optional<ItemImage> findForUpdate(long userId, UUID imageId);

  /** 그 아이템에 붙은 사진 (행을 잠근다) */
  Optional<ItemImage> findByItemForUpdate(long userId, long itemId);

  /** 아이템들에 붙은 사진 */
  List<ItemImage> findByItems(long userId, Collection<Long> itemIds);

  ItemImage save(ItemImage image);

  /**
   * 정리 작업이 지울 이미지를 먼저 DELETED로 바꾼다 (아이템에 안 붙은 것만, 한 트랜잭션). 그사이 아이템에 붙은 이미지는 빠진다. DELETED가 된(이미
   * DELETED였던 것 포함) ID를 돌려준다
   */
  List<UUID> claimForCleanup(Collection<UUID> imageIds, Instant now);

  void deleteAll(Collection<UUID> imageIds);
}
