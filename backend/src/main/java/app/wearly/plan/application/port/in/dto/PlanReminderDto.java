package app.wearly.plan.application.port.in.dto;

import app.wearly.plan.domain.Plan;
import app.wearly.plan.domain.PlanInput;
import java.time.LocalDate;

/**
 * 알림에 쓰는 예정 하나 (코디·메모는 빼고 알림 문구와 날씨 다시 확인에 필요한 값만).
 *
 * @param clientId 앱이 만든 예정 ID. 없으면 null (앱은 srv-&lt;id&gt;로 부른다)
 * @param weatherSource FORECAST · CLIMATE · MANUAL 또는 null(예전 앱 데이터)
 */
public record PlanReminderDto(
    long id,
    long userId,
    String clientId,
    LocalDate date,
    String timeOfDay,
    String title,
    PlanPlaceDto place,
    PlanWeatherDto weather,
    String weatherSource) {

  public static PlanReminderDto from(Plan plan) {
    PlanInput in = plan.input();
    return new PlanReminderDto(
        plan.id(),
        plan.userId(),
        plan.clientId(),
        in.date(),
        in.timeOfDay(),
        in.title(),
        PlanPlaceDto.from(in.place()),
        PlanWeatherDto.from(in.weather()),
        in.weather().source());
  }
}
