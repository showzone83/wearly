package app.wearly.plan.adapter.out.persistence.jpa;

import app.wearly.plan.adapter.out.persistence.PlanWeatherJson;
import app.wearly.plan.application.port.out.PlanPort;
import app.wearly.plan.domain.Plan;
import app.wearly.plan.domain.PlanInput;
import app.wearly.plan.domain.PlanPlace;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** PlanPort 구현 (JPA). 엔티티 ↔ 도메인 모델 변환 */
@Component
class PlanJpaAdapter implements PlanPort {

  private final PlanJpaRepository plans;

  PlanJpaAdapter(PlanJpaRepository plans) {
    this.plans = plans;
  }

  @Override
  public Optional<Plan> find(long userId, long planId) {
    return plans.findByIdAndUserId(planId, userId).map(PlanJpaAdapter::toDomain);
  }

  @Override
  public List<Plan> findByClientIds(long userId, Collection<String> clientIds) {
    if (clientIds.isEmpty()) {
      return List.of();
    }
    return plans.findByUserIdAndClientIdIn(userId, clientIds).stream()
        .map(PlanJpaAdapter::toDomain)
        .toList();
  }

  @Override
  public Plan save(Plan plan) {
    PlanEntity e =
        plan.id() == null
            ? new PlanEntity(plan.userId(), plan.clientId())
            : plans
                .findById(plan.id())
                .orElseThrow(() -> new IllegalStateException("예정이 없어요: " + plan.id()));
    PlanInput in = plan.input();
    PlanPlace p = in.place();
    e.apply(
        in.date(),
        in.timeOfDay(),
        in.title(),
        in.occasion(),
        in.concepts(),
        in.memo(),
        p == null ? null : p.regionCode(),
        p == null ? null : p.label(),
        p == null ? null : p.lat(),
        p == null ? null : p.lon(),
        PlanWeatherJson.toJson(in.weather()),
        in.forecastReady(),
        in.outfit());
    return toDomain(plans.saveAndFlush(e));
  }

  @Override
  public void delete(Plan plan) {
    plans.findById(plan.id()).ifPresent(plans::delete);
    plans.flush();
  }

  static Plan toDomain(PlanEntity e) {
    return new Plan(
        e.getId(),
        e.getUserId(),
        e.getClientId(),
        new PlanInput(
            e.getPlannedDate(),
            e.getTimeOfDay(),
            e.getTitle(),
            e.getOccasion(),
            e.getConcepts(),
            e.getMemo(),
            e.getPlaceLabel() == null
                ? null
                : new PlanPlace(
                    e.getPlaceRegionCode(), e.getPlaceLabel(), e.getPlaceLat(), e.getPlaceLon()),
            PlanWeatherJson.fromJson(e.getWeather()),
            e.isForecastReady(),
            e.getOutfit()),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
