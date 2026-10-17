package app.wearly.wardrobe;

import app.wearly.common.Ids;
import app.wearly.config.ClockConfig;
import app.wearly.domain.Category;
import app.wearly.domain.ColorKey;
import app.wearly.domain.ItemStatus;
import app.wearly.domain.StyleTag;
import app.wearly.wardrobe.WardrobeDtos.CountByColor;
import app.wearly.wardrobe.WardrobeDtos.CountByStyle;
import app.wearly.wardrobe.WardrobeDtos.WardrobeStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** /wardrobe/stats 집계 (MyBatis). 날짜 기준은 Asia/Seoul */
@Service
public class WardrobeStatsService {

    static final int WORN_WINDOW_DAYS = 30;
    static final int SLEEPING_DAYS = 60;
    static final int SLEEPING_LIMIT = 10;

    private final WardrobeQueryMapper queries;
    private final Clock clock;

    public WardrobeStatsService(WardrobeQueryMapper queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WardrobeStats stats(long userId) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        long total = queries.countAll(userId);

        Map<Category, Long> byCategory = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            byCategory.put(c, 0L);
        }
        queries.countByCategory(userId).forEach(r -> byCategory.put(Category.valueOf(r.getKey()), r.getCount()));

        Map<ItemStatus, Long> byStatus = new EnumMap<>(ItemStatus.class);
        for (ItemStatus s : ItemStatus.values()) {
            byStatus.put(s, 0L);
        }
        queries.countByStatus(userId).forEach(r -> byStatus.put(ItemStatus.valueOf(r.getKey()), r.getCount()));

        List<CountByColor> byColor = queries.countByColor(userId).stream()
                .map(r -> new CountByColor(ColorKey.fromCode(r.getKey()), r.getCount()))
                .toList();
        List<CountByStyle> byStyle = queries.countByStyle(userId).stream()
                .map(r -> new CountByStyle(StyleTag.valueOf(r.getKey()), r.getCount()))
                .toList();

        // 오늘을 포함한 최근 30일: today-29 ~ today
        long worn = queries.countWornSince(userId, today.minusDays(WORN_WINDOW_DAYS - 1));
        double utilization = total == 0 ? 0.0
                : BigDecimal.valueOf(worn).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
        // 60일 넘게 안 입음: 마지막 착용일이 today-60보다 이전
        List<String> sleeping = queries.findSleepingIds(userId, today.minusDays(SLEEPING_DAYS), SLEEPING_LIMIT).stream()
                .map(Ids::format)
                .toList();

        return new WardrobeStats(total, byCategory, byColor, byStyle, byStatus, worn, utilization, sleeping);
    }
}
