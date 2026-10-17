package app.wearly.wardrobe.application.port.in;

import app.wearly.wardrobe.application.port.in.dto.ItemCodeCountDto;
import java.util.List;

/**
 * 옷장 아이템의 코드 사용 수 (모든 회원). code 컨텍스트의 사용 수 집계·삭제 가드가 입력 어댑터(adapter.in.code)를 거쳐 부른다 (H1). 아이템의 어느
 * 컬럼이 어느 코드 그룹을 담는지는 옷장만 안다.
 */
public interface ItemCodeUsageUseCase {

  List<ItemCodeCountDto> countByCode();

  long countItemsUsing(String group, String code);
}
