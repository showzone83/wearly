package app.wearly.plan.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 검증을 마친 예정 코디 값 (저장할 모양).
 *
 * @param memo 없으면 null
 * @param place 없으면 null (내 지역)
 * @param outfit 없으면 null
 */
public record PlanInput(
    LocalDate date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    PlanPlace place,
    PlanWeather weather,
    boolean forecastReady,
    Map<String, Object> outfit) {

  public PlanInput {
    concepts = List.copyOf(concepts);
  }
}
