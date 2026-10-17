package app.wearly.wardrobe;

import app.wearly.common.Ids;
import app.wearly.config.ClockConfig;
import app.wearly.code.CodeCatalog;
import app.wearly.code.CodeEntry;
import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.wardrobe.WardrobeDtos.CountByColor;
import app.wearly.wardrobe.WardrobeDtos.CountByStyle;
import app.wearly.wardrobe.WardrobeDtos.WardrobeStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final CodeCatalog catalog;

    public WardrobeStatsService(WardrobeQueryMapper queries, Clock clock, CodeCatalog catalog) {
        this.queries = queries;
        this.clock = clock;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public WardrobeStats stats(long userId) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
        long total = queries.countAll(userId);

        CodeSnapshot codes = catalog.current();
        Map<String, Long> byCategory = keyed(codes, CodeGroups.CATEGORY, queries.countByCategory(userId));
        Map<String, Long> byStatus = keyed(codes, CodeGroups.ITEM_STATUS, queries.countByStatus(userId));

        List<CountByColor> byColor = queries.countByColor(userId).stream()
                .map(r -> new CountByColor(r.getKey(), r.getCount()))
                .toList();
        List<CountByStyle> byStyle = queries.countByStyle(userId).stream()
                .map(r -> new CountByStyle(r.getKey(), r.getCount()))
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

    /**
     * 활성 코드는 0이어도 키로 넣고, 비활성 코드는 아이템이 있을 때만 넣는다 (06 문서 4장). 순서는 코드 순서.
     * 코드 테이블에 없는 값이 저장돼 있으면(이론상 없음) 맨 뒤에 붙인다.
     */
    static Map<String, Long> keyed(CodeSnapshot codes, String group, List<CountRow> rows) {
        Map<String, Long> counts = new HashMap<>();
        rows.forEach(r -> counts.put(r.getKey(), r.getCount()));
        Map<String, Long> out = new LinkedHashMap<>();
        for (CodeEntry c : codes.codes(group)) {
            long n = counts.getOrDefault(c.code(), 0L);
            if (c.active() || n > 0) {
                out.put(c.code(), n);
            }
        }
        counts.forEach((k, v) -> out.putIfAbsent(k, v));
        return out;
    }
}
