package app.wearly.wardrobe.application.service;

import app.wearly.wardrobe.application.port.in.ItemExistenceUseCase;
import app.wearly.wardrobe.application.port.out.ItemExistencePort;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아이템 존재 확인. 빈 목록이면 DB를 부르지 않는다 */
@Service
public class ItemExistenceService implements ItemExistenceUseCase {

  private final ItemExistencePort items;

  public ItemExistenceService(ItemExistencePort items) {
    this.items = items;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<Long> existingIds(long userId, Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }
    return items.findExistingIds(userId, Set.copyOf(ids));
  }
}
