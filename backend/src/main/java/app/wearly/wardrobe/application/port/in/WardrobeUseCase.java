package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.domain.ItemInput;
import java.util.List;

/** 옷장 아이템 (05 문서 6장). 코드 값은 코드 테이블로 검증한다 (06 문서 4장) */
public interface WardrobeUseCase {

  List<ItemView> list(ListItemsQuery query);

  ItemView get(long userId, long itemId);

  ItemView create(long userId, ItemInput input);

  /** 요청 순서대로 저장하고 같은 순서로 돌려준다. 하나라도 틀리면 전부 저장하지 않는다 */
  List<ItemView> bulkCreate(long userId, List<ItemInput> inputs);

  /** 저장된 값 위에 보낸 필드만 덮어쓴 뒤 등록과 같은 검증을 한다. PATCH에서 안 바꾼 비활성 코드는 통과 */
  ItemView patch(long userId, long itemId, ItemPatch patch);

  void delete(long userId, long itemId);
}
