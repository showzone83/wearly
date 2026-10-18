package app.wearly.wardrobe.application.port.in;

import java.util.Collection;
import java.util.Set;

/** 아이템이 내 옷장에 있는지 (사용 중지한 옷 포함, 완전 삭제한 옷은 없음). 예정 코디가 지워진 아이템을 표시할 때 쓴다 */
public interface ItemExistenceUseCase {

  /** ids 중 이 회원의 옷장에 있는 ID */
  Set<Long> existingIds(long userId, Collection<Long> ids);
}
