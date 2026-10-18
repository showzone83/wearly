package app.wearly.plan.adapter.in.web.request;

import app.wearly.plan.application.port.in.dto.PlanInputDto;
import java.util.List;
import java.util.Map;

/**
 * POST /plans 본문과 bulk 항목 (앱 PlannedEvent에서 id 대신 clientId). 값 검증(필수·길이·범위·코드)은 유스케이스가 필드 오류 코드로
 * 한다. outfit은 앱 Outfit 모양 그대로 받는다 (items: 칸 → 아이템 ID)
 */
public record PlanRequest(
    String clientId,
    String date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    PlanPlaceRequest place,
    PlanWeatherRequest weather,
    String weatherSource,
    String weatherCheckedAt,
    Boolean forecastReady,
    Map<String, Object> outfit) {

  public PlanInputDto toDto() {
    return new PlanInputDto(
        clientId,
        date,
        timeOfDay,
        title,
        occasion,
        concepts,
        memo,
        place == null ? null : place.toDto(),
        weather == null ? null : weather.toDto(),
        weatherSource,
        weatherCheckedAt,
        forecastReady,
        outfit);
  }
}
