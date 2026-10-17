package app.wearly.wardrobe.domain;

import app.wearly.common.domain.code.CodeEntry;
import app.wearly.common.domain.code.CodeSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /wardrobe/stats 집계 규칙 (05 문서 6장, 06 문서 4장) */
public final class WardrobeStatsRules {

  /** 최근 착용: 오늘을 포함한 30일 */
  public static final int WORN_WINDOW_DAYS = 30;

  /** 잠든 옷: 60일 넘게 안 입음 */
  public static final int SLEEPING_DAYS = 60;

  public static final int SLEEPING_LIMIT = 10;

  private WardrobeStatsRules() {}

  /**
   * 활성 코드는 0이어도 키로 넣고, 비활성 코드는 아이템이 있을 때만 넣는다. 순서는 코드 순서. 코드 테이블에 없는 값이 저장돼 있으면(이론상 없음) 맨 뒤에 붙인다.
   */
  public static Map<String, Long> keyed(CodeSnapshot codes, String group, List<KeyCount> rows) {
    Map<String, Long> counts = new HashMap<>();
    rows.forEach(r -> counts.put(r.key(), r.count()));
    Map<String, Long> out = new LinkedHashMap<>();
    for (CodeEntry c : codes.codes(group)) {
      long n = counts.getOrDefault(c.code(), 0L);
      if (c.active() || n > 0) {
        out.put(c.code(), n);
      }
    }
    counts.forEach(out::putIfAbsent);
    return out;
  }

  /** 활용률 = 최근 착용 수 / 전체, 소수 둘째 자리 반올림. 아이템이 없으면 0 */
  public static double utilization(long worn, long total) {
    return total == 0
        ? 0.0
        : BigDecimal.valueOf(worn)
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
            .doubleValue();
  }
}
