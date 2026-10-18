package app.wearly.plan.adapter.in.web.response;

import app.wearly.plan.application.port.in.dto.PlanDto;
import java.util.List;

/** GET /plans 응답 { items } */
public record PlanListResponse(List<PlanResponse> items) {

  public static PlanListResponse from(List<PlanDto> plans) {
    return new PlanListResponse(plans.stream().map(PlanResponse::from).toList());
  }
}
