package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.application.port.in.dto.CreateItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemDto;
import app.wearly.wardrobe.application.port.in.dto.ItemSearchDto;
import app.wearly.wardrobe.application.port.in.dto.PatchItemDto;
import app.wearly.wardrobe.application.port.in.dto.RetireItemDto;
import java.util.List;

/** 옷장 아이템 (05 문서 6장). 코드 값은 코드 테이블로 검증한다 (06 문서 4장) */
public interface WardrobeUseCase {

  /** 기본은 사용 중인 옷만 (retired=false). 추천 후보처럼 사용 중지 옷이 필요 없으면 기본값을 쓴다 */
  List<ItemDto> list(ItemSearchDto search);

  ItemDto get(long userId, long itemId);

  ItemDto create(long userId, CreateItemDto input);

  /** 요청 순서대로 저장하고 같은 순서로 돌려준다. 하나라도 틀리면 전부 저장하지 않는다 */
  List<ItemDto> bulkCreate(long userId, List<CreateItemDto> inputs);

  /**
   * 저장된 값 위에 보낸 필드만 덮어쓴 뒤 등록과 같은 검증을 한다. 없는 아이템이면 404가 먼저, 그다음 입력 형식 오류(400). PATCH에서 안 바꾼 비활성 코드는
   * 통과
   */
  ItemDto patch(long userId, long itemId, PatchItemDto patch);

  /** 완전 삭제 (13 문서). 되돌릴 수 없다 */
  void delete(long userId, long itemId);

  /**
   * 사용하지 않는 옷으로 옮긴다 (13 문서 3장). 없는 아이템이면 404가 먼저, 그다음 입력 오류(이유가 활성 RETIRE_REASON 코드가 아님, 메모 200자
   * 초과) 400. 이미 사용 중지면 이유·메모만 바꾸고 시각은 그대로
   */
  ItemDto retire(long userId, long itemId, RetireItemDto input);

  /** 다시 사용한다. 사용 중이면 그대로 돌려준다. 없는 아이템이면 404 */
  ItemDto restore(long userId, long itemId);
}
