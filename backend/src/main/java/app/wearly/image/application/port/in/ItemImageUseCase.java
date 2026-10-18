package app.wearly.image.application.port.in;

import app.wearly.image.application.port.in.dto.ItemImageRefDto;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 아이템 대표 사진 (wardrobe 컨텍스트가 부른다, 17 문서 3장). 아이템 쪽 트랜잭션 안에서 부르고, 버린 사진의 객체는 커밋 뒤에 지운다. */
public interface ItemImageUseCase {

  /**
   * 아이템에 사진을 붙인다 (이미 다른 사진이 있으면 바꾸고 예전 사진은 DELETED + 객체 삭제). 붙일 수 있는 것은 내 READY 이미지이고 다른 아이템에 붙지 않은
   * 것: 없거나 남의 것·지운 것은 404, PENDING이면 400 IMAGE_NOT_UPLOADED, 다른 아이템에 붙었으면 409 IMAGE_IN_USE. 이미 이
   * 아이템에 붙어 있으면 그대로
   */
  ItemImageRefDto attach(long userId, long itemId, String imageId);

  /** 아이템에서 사진을 뺀다 (DELETED + 객체 삭제). 사진이 없으면 아무것도 안 한다. 아이템 완전 삭제 전에도 부른다 */
  void detach(long userId, long itemId);

  Optional<ItemImageRefDto> find(long userId, long itemId);

  /** 아이템 ID → 사진. 사진이 없는 아이템은 빠진다 */
  Map<Long, ItemImageRefDto> findAll(long userId, Collection<Long> itemIds);
}
