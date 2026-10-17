package app.wearly.wardrobe.adapter.out.query;

import app.wearly.wardrobe.application.port.out.ItemFilter;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.domain.KeyCount;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** ItemQueryPort 구현 (MyBatis). 행 → 도메인 모델 변환 */
@Component
class ItemQueryAdapter implements ItemQueryPort {

    private final WardrobeQueryMapper mapper;

    ItemQueryAdapter(WardrobeQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WardrobeItem> findItems(ItemFilter filter) {
        return mapper.findItems(filter).stream().map(r -> toDomain(r, filter.userId())).toList();
    }

    @Override
    public long countAll(long userId) {
        return mapper.countAll(userId);
    }

    @Override
    public List<KeyCount> countByCategory(long userId) {
        return counts(mapper.countByCategory(userId));
    }

    @Override
    public List<KeyCount> countByColor(long userId) {
        return counts(mapper.countByColor(userId));
    }

    @Override
    public List<KeyCount> countByStyle(long userId) {
        return counts(mapper.countByStyle(userId));
    }

    @Override
    public List<KeyCount> countByStatus(long userId) {
        return counts(mapper.countByStatus(userId));
    }

    @Override
    public long countWornSince(long userId, LocalDate since) {
        return mapper.countWornSince(userId, since);
    }

    @Override
    public List<Long> findSleepingIds(long userId, LocalDate before, int limit) {
        return mapper.findSleepingIds(userId, before, limit);
    }

    private static List<KeyCount> counts(List<CountRow> rows) {
        return rows.stream().map(r -> new KeyCount(r.getKey(), r.getCount())).toList();
    }

    private static WardrobeItem toDomain(ItemRow r, long userId) {
        return new WardrobeItem(r.getId(), userId, r.getName(), r.getCategory(), r.getSubType(), r.getColor(), r.getPattern(),
                r.getSeasons(), r.getStyles(), r.getFormality(), r.getStatus(), r.getBrand(), r.getWornCount(),
                r.getLastWornAt(), r.getCreatedAt());
    }
}
