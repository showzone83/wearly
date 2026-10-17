package app.wearly.outfit.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI가 매긴 5개 기준 0~100 예상 점수 (참고용. 화면 점수는 앱 엔진이 다시 매긴다) */
public record AiScores(int color, int occasion, int weather, int taste, int diversity) {

  public static final List<String> FIELDS =
      List.of("color", "occasion", "weather", "taste", "diversity");

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("color", color);
    m.put("occasion", occasion);
    m.put("weather", weather);
    m.put("taste", taste);
    m.put("diversity", diversity);
    return m;
  }
}
