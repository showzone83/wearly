package app.wearly.wardrobe.application.port.out;

import app.wearly.wardrobe.domain.KeyCount;
import app.wearly.wardrobe.domain.WardrobeItem;
import java.time.LocalDate;
import java.util.List;

/** 옷장 목록·통계 조회 (MyBatis, 읽기 전용) */
public interface ItemQueryPort {

    List<WardrobeItem> findItems(ItemFilter filter);

    long countAll(long userId);

    List<KeyCount> countByCategory(long userId);

    /** 많은 순, 같으면 색 코드 순 */
    List<KeyCount> countByColor(long userId);

    /** 많은 순, 같으면 스타일 코드 순 */
    List<KeyCount> countByStyle(long userId);

    List<KeyCount> countByStatus(long userId);

    long countWornSince(long userId, LocalDate since);

    /** 한 번도 안 입었거나 기준일 전에 마지막으로 입은 아이템. 오래 잠든 것부터 */
    List<Long> findSleepingIds(long userId, LocalDate before, int limit);
}
