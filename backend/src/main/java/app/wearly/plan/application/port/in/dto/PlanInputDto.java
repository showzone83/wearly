package app.wearly.plan.application.port.in.dto;

import app.wearly.plan.domain.PlanDraft;
import java.util.List;
import java.util.Map;

/** 예정 등록·bulk 항목 입력 (검증 전 값 그대로). 검증은 유스케이스가 한다 */
public record PlanInputDto(
    String clientId,
    String date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    PlanPlaceDto place,
    PlanWeatherDto weather,
    String weatherSource,
    String weatherCheckedAt,
    Boolean forecastReady,
    Map<String, Object> outfit) {

  public PlanDraft toDraft() {
    return new PlanDraft(
        clientId,
        date,
        timeOfDay,
        title,
        occasion,
        concepts,
        memo,
        place == null ? null : place.toDraft(),
        weather == null ? null : weather.toDraft(),
        weatherSource,
        weatherCheckedAt,
        forecastReady,
        outfit);
  }
}
