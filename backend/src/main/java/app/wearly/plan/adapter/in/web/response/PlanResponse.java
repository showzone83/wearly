package app.wearly.plan.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.plan.application.port.in.dto.PlanDto;
import java.util.List;
import java.util.Map;

/**
 * 예정 코디 응답 (앱 PlannedEvent 모양 + 서버 값). id는 서버 ID(문자열), clientId는 앱이 보낸 값 또는 null. 없는 값은 null로 보낸다.
 * missingItemIds: outfit.items의 아이템 중 지금 옷장에 없는 ID (코디는 그대로 둔다)
 */
public record PlanResponse(
    String id,
    String clientId,
    String date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    PlanPlaceResponse place,
    PlanWeatherResponse weather,
    String weatherSource,
    String weatherCheckedAt,
    boolean forecastReady,
    Map<String, Object> outfit,
    List<String> missingItemIds,
    String createdAt,
    String updatedAt) {

  public static PlanResponse from(PlanDto p) {
    return new PlanResponse(
        Ids.format(p.id()),
        p.clientId(),
        p.date().toString(),
        p.timeOfDay(),
        p.title(),
        p.occasion(),
        p.concepts(),
        p.memo(),
        PlanPlaceResponse.from(p.place()),
        PlanWeatherResponse.from(p.weather()),
        p.weatherSource(),
        Times.iso(p.weatherCheckedAt()),
        p.forecastReady(),
        p.outfit(),
        p.missingItemIds(),
        Times.iso(p.createdAt()),
        Times.iso(p.updatedAt()));
  }
}
