package app.wearly.wardrobe.application.service;

import app.wearly.code.application.port.in.CodeCatalog;
import app.wearly.common.domain.Zones;
import app.wearly.common.domain.code.CodeGroups;
import app.wearly.common.domain.code.CodeSnapshot;
import app.wearly.wardrobe.application.port.in.WardrobeStatsUseCase;
import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto;
import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto.ColorCount;
import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto.StyleCount;
import app.wearly.wardrobe.application.port.out.ItemQueryPort;
import app.wearly.wardrobe.domain.WardrobeStatsRules;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** /wardrobe/stats 집계 (MyBatis). 날짜 기준은 Asia/Seoul */
@Service
public class WardrobeStatsService implements WardrobeStatsUseCase {

  private final ItemQueryPort queries;
  private final Clock clock;
  private final CodeCatalog catalog;

  public WardrobeStatsService(ItemQueryPort queries, Clock clock, CodeCatalog catalog) {
    this.queries = queries;
    this.clock = clock;
    this.catalog = catalog;
  }

  @Override
  @Transactional(readOnly = true)
  public WardrobeStatsDto stats(long userId) {
    LocalDate today = Zones.today(clock);
    long total = queries.countAll(userId);

    CodeSnapshot codes = catalog.current();
    Map<String, Long> byCategory =
        WardrobeStatsRules.keyed(codes, CodeGroups.CATEGORY, queries.countByCategory(userId));
    Map<String, Long> byStatus =
        WardrobeStatsRules.keyed(codes, CodeGroups.ITEM_STATUS, queries.countByStatus(userId));

    List<ColorCount> byColor =
        queries.countByColor(userId).stream().map(r -> new ColorCount(r.key(), r.count())).toList();
    List<StyleCount> byStyle =
        queries.countByStyle(userId).stream().map(r -> new StyleCount(r.key(), r.count())).toList();

    // 오늘을 포함한 최근 30일: today-29 ~ today
    long worn =
        queries.countWornSince(userId, today.minusDays(WardrobeStatsRules.WORN_WINDOW_DAYS - 1));
    double utilization = WardrobeStatsRules.utilization(worn, total);
    // 60일 넘게 안 입음: 마지막 착용일이 today-60보다 이전
    List<Long> sleeping =
        queries
            .findSleepingIds(
                userId,
                today.minusDays(WardrobeStatsRules.SLEEPING_DAYS),
                WardrobeStatsRules.SLEEPING_LIMIT)
            .stream()
            .toList();

    return new WardrobeStatsDto(
        total, byCategory, byColor, byStyle, byStatus, worn, utilization, sleeping);
  }
}
