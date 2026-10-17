package app.wearly.wardrobe.application.port.out;

import app.wearly.wardrobe.domain.WardrobeItem;
import java.util.Optional;

/** 옷장 아이템 쓰기·단건 로드 (JPA) */
public interface ItemPort {

  /** 남의 아이템은 찾지 못한 것으로 본다 (404) */
  Optional<WardrobeItem> find(long userId, long itemId);

  /** 새 아이템이면 넣고, 있으면 바꾼다. 저장된 모습(id·createdAt 포함)을 돌려준다 */
  WardrobeItem save(WardrobeItem item);

  void delete(WardrobeItem item);
}
