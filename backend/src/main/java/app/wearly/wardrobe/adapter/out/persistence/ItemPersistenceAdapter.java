package app.wearly.wardrobe.adapter.out.persistence;

import app.wearly.wardrobe.application.port.out.ItemPort;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** ItemPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class ItemPersistenceAdapter implements ItemPort {

    private final WardrobeItemRepository items;

    ItemPersistenceAdapter(WardrobeItemRepository items) {
        this.items = items;
    }

    @Override
    public Optional<WardrobeItem> find(long userId, long itemId) {
        return items.findByIdAndUserId(itemId, userId).map(ItemPersistenceAdapter::toDomain);
    }

    @Override
    public WardrobeItem save(WardrobeItem item) {
        WardrobeItemEntity e = item.id() == null ? new WardrobeItemEntity(item.userId())
                : items.findById(item.id()).orElseThrow(() -> new IllegalStateException("아이템이 없어요: " + item.id()));
        e.apply(item.name(), item.category(), item.subType(), item.color(), item.pattern(), item.seasons(), item.styles(),
                (short) item.formality(), item.status(), item.brand(), item.wornCount(), item.lastWornAt());
        return toDomain(items.saveAndFlush(e));
    }

    @Override
    public void delete(WardrobeItem item) {
        items.findById(item.id()).ifPresent(items::delete);
    }

    static WardrobeItem toDomain(WardrobeItemEntity e) {
        return new WardrobeItem(e.getId(), e.getUserId(), e.getName(), e.getCategory(), e.getSubType(), e.getColor(),
                e.getPattern(), e.getSeasons(), e.getStyles(), e.getFormality(), e.getStatus(), e.getBrand(),
                e.getWornCount(), e.getLastWornAt(), e.getCreatedAt());
    }
}
