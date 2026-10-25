package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;
import java.util.List;

/**
 * 돕는 사람이 옷장 주인의 옷장을 쓴다 (doc/26 2단계, 옷장 도우미). 먼저 연결·권한을 확인하고(연결 없음 404, 권한 부족 403) 주인 옷장에서 {@link
 * WardrobeUseCase}와 같은 일을 한다. 바꾸는 일은 같은 트랜잭션에서 대리 작업 기록을 남긴다. 완전 삭제는 없다 (주인만).
 */
public interface OwnerWardrobeUseCase {

  /** 보기 권한. search.userId는 주인 */
  List<ItemDto> list(long helperId, ItemSearchDto search);

  /** 보기 권한 */
  ItemDto get(long helperId, long ownerId, long itemId);

  /** 등록·수정 권한 */
  ItemDto create(long helperId, long ownerId, CreateItemDto input);

  /** 등록·수정 권한 (사진 바꾸기 포함) */
  ItemDto patch(long helperId, long ownerId, long itemId, PatchItemDto patch);

  /** 등록·수정 권한 */
  ItemDto retire(long helperId, long ownerId, long itemId, RetireItemDto input);

  /** 등록·수정 권한. 이미 사용 중이면 기록하지 않는다 */
  ItemDto restore(long helperId, long ownerId, long itemId);
}
