package app.wearly.wardrobe.application.port.in;

import java.util.List;
import java.util.Map;

/** /wardrobe/stats. byCategory·byStatus의 키는 활성 코드 전부(0 포함) + 아이템이 있는 비활성 코드, 코드 순서대로 */
public record WardrobeStats(
    long total,
    Map<String, Long> byCategory,
    List<CountByColor> byColor,
    List<CountByStyle> byStyle,
    Map<String, Long> byStatus,
    long wornLast30Days,
    double utilization30d,
    List<String> sleeping) {

  public record CountByColor(String color, long count) {}

  public record CountByStyle(String style, long count) {}
}
