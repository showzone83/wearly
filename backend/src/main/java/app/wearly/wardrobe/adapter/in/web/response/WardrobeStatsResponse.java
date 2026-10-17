package app.wearly.wardrobe.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.wardrobe.application.port.in.dto.WardrobeStatsDto;
import java.util.List;
import java.util.Map;

/**
 * GET /wardrobe/stats (05 문서 6장, 13 문서 3장). sleeping은 아이템 ID 문자열. retired·retiredByReason 말고는 사용 중인
 * 옷만 센다
 */
public record WardrobeStatsResponse(
    long total,
    Map<String, Long> byCategory,
    List<ColorCount> byColor,
    List<StyleCount> byStyle,
    Map<String, Long> byStatus,
    long wornLast30Days,
    double utilization30d,
    List<String> sleeping,
    long retired,
    List<ReasonCount> retiredByReason) {

  public record ColorCount(String color, long count) {}

  public record StyleCount(String style, long count) {}

  public record ReasonCount(String reason, long count) {}

  public static WardrobeStatsResponse from(WardrobeStatsDto d) {
    return new WardrobeStatsResponse(
        d.total(),
        d.byCategory(),
        d.byColor().stream().map(c -> new ColorCount(c.color(), c.count())).toList(),
        d.byStyle().stream().map(s -> new StyleCount(s.style(), s.count())).toList(),
        d.byStatus(),
        d.wornLast30Days(),
        d.utilization30d(),
        d.sleeping().stream().map(Ids::format).toList(),
        d.retired(),
        d.retiredByReason().stream().map(r -> new ReasonCount(r.reason(), r.count())).toList());
  }
}
