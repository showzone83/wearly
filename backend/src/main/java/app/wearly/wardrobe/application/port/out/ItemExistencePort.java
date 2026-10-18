package app.wearly.wardrobe.application.port.out;

import java.util.Collection;
import java.util.Set;

/** 아이템 존재 확인 (MyBatis) */
public interface ItemExistencePort {

  /** ids 중 이 회원의 아이템 ID (사용 중지 포함) */
  Set<Long> findExistingIds(long userId, Collection<Long> ids);
}
