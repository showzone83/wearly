package app.wearly.plan.adapter.out.persistence;

import app.wearly.plan.domain.PlanWeather;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * tb_planned_outfit.weather(JSONB) ↔ PlanWeather. 모양은 앱 Weather 그대로에 source·checkedAt을 더한 것: {
 * temp, min, max, rainProb, cond, manual, source, checkedAt }. JPA·MyBatis 어댑터가 같이 쓴다
 */
public final class PlanWeatherJson {

  private PlanWeatherJson() {}

  public static Map<String, Object> toJson(PlanWeather w) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("temp", w.temp());
    m.put("min", w.min());
    m.put("max", w.max());
    m.put("rainProb", w.rainProb());
    m.put("cond", w.cond());
    m.put("manual", w.manual());
    m.put("source", w.source());
    m.put("checkedAt", w.checkedAt() == null ? null : w.checkedAt().toString());
    return m;
  }

  public static PlanWeather fromJson(Map<String, Object> m) {
    Object checkedAt = m.get("checkedAt");
    return new PlanWeather(
        number(m.get("temp")),
        number(m.get("min")),
        number(m.get("max")),
        number(m.get("rainProb")),
        (String) m.get("cond"),
        Boolean.TRUE.equals(m.get("manual")),
        (String) m.get("source"),
        checkedAt == null ? null : Instant.parse(checkedAt.toString()));
  }

  private static double number(Object v) {
    return v instanceof Number n ? n.doubleValue() : 0;
  }
}
