package app.wearly.plan.application.port.in.dto;

import app.wearly.plan.domain.Plan;
import app.wearly.plan.domain.PlanInput;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 예정 코디 하나.
 *
 * @param missingItemIds 코디(outfit.items)의 아이템 중 지금 옷장에 없는 ID (지웠거나 기기에서만 만든 ID). 코디는 그대로 둔다
 */
public record PlanDto(
    long id,
    String clientId,
    LocalDate date,
    String timeOfDay,
    String title,
    String occasion,
    List<String> concepts,
    String memo,
    PlanPlaceDto place,
    PlanWeatherDto weather,
    String weatherSource,
    Instant weatherCheckedAt,
    boolean forecastReady,
    Map<String, Object> outfit,
    List<String> missingItemIds,
    Instant createdAt,
    Instant updatedAt) {

  public static PlanDto from(Plan plan, List<String> missingItemIds) {
    PlanInput in = plan.input();
    return new PlanDto(
        plan.id(),
        plan.clientId(),
        in.date(),
        in.timeOfDay(),
        in.title(),
        in.occasion(),
        in.concepts(),
        in.memo(),
        PlanPlaceDto.from(in.place()),
        PlanWeatherDto.from(in.weather()),
        in.weather().source(),
        in.weather().checkedAt(),
        in.forecastReady(),
        in.outfit(),
        List.copyOf(missingItemIds),
        plan.createdAt(),
        plan.updatedAt());
  }
}
