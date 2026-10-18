package app.wearly.plan.adapter.out.persistence.mybatis;

import app.wearly.plan.adapter.out.persistence.PlanWeatherJson;
import app.wearly.plan.adapter.out.persistence.mybatis.dto.PlanRowDto;
import app.wearly.plan.adapter.out.persistence.mybatis.mapper.PlanMapper;
import app.wearly.plan.application.port.out.PlanFilter;
import app.wearly.plan.application.port.out.PlanQueryPort;
import app.wearly.plan.application.port.out.dto.PlanCodeCountRowDto;
import app.wearly.plan.domain.Plan;
import app.wearly.plan.domain.PlanInput;
import app.wearly.plan.domain.PlanPlace;
import java.util.List;
import org.springframework.stereotype.Component;

/** PlanQueryPort 구현 (MyBatis). 목록 행 → 도메인 모델 변환 */
@Component
class PlanMybatisAdapter implements PlanQueryPort {

  private final PlanMapper mapper;

  PlanMybatisAdapter(PlanMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<Plan> findPlans(PlanFilter filter) {
    return mapper.findPlans(filter).stream().map(PlanMybatisAdapter::toDomain).toList();
  }

  @Override
  public long count(long userId) {
    return mapper.count(userId);
  }

  @Override
  public void lockUser(long userId) {
    mapper.lockUser(userId);
  }

  @Override
  public List<PlanCodeCountRowDto> countByCode() {
    return mapper.countByCode();
  }

  @Override
  public long countUsing(String group, String code) {
    return mapper.countUsing(group, code);
  }

  static Plan toDomain(PlanRowDto r) {
    return new Plan(
        r.getId(),
        r.getUserId(),
        r.getClientId(),
        new PlanInput(
            r.getPlannedDate(),
            r.getTimeOfDay(),
            r.getTitle(),
            r.getOccasion(),
            r.getConcepts() == null ? List.of() : r.getConcepts(),
            r.getMemo(),
            r.getPlaceLabel() == null
                ? null
                : new PlanPlace(
                    r.getPlaceRegionCode(), r.getPlaceLabel(), r.getPlaceLat(), r.getPlaceLon()),
            PlanWeatherJson.fromJson(r.getWeather()),
            r.isForecastReady(),
            r.getOutfit()),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
