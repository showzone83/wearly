package app.wearly.wardrobe.application.port.in.dto;

import java.util.List;
import java.util.Map;

/**
 * 옷장 통계 (유스케이스 출력). retired·retiredByReason 말고는 사용 중인 옷만 센다 (13 문서 3장). byCategory·byStatus의 키는 활성
 * 코드 전부(0 포함) + 아이템이 있는 비활성 코드, 코드 순서대로. sleeping은 오래 잠든 아이템 ID. retiredByReason은 많은 순
 */
public record WardrobeStatsDto(
    long total,
    Map<String, Long> byCategory,
    List<ColorCount> byColor,
    List<StyleCount> byStyle,
    Map<String, Long> byStatus,
    long wornLast30Days,
    double utilization30d,
    List<Long> sleeping,
    long retired,
    List<ReasonCount> retiredByReason) {

  public record ColorCount(String color, long count) {}

  public record StyleCount(String style, long count) {}

  public record ReasonCount(String reason, long count) {}
}
