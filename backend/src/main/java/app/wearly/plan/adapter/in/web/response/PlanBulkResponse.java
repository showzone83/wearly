package app.wearly.plan.adapter.in.web.response;

import app.wearly.plan.application.port.in.dto.PlanBulkResultDto;
import java.util.List;

/** POST /plans/bulk 응답 { items(요청 순서), created, updated } */
public record PlanBulkResponse(List<PlanResponse> items, int created, int updated) {

  public static PlanBulkResponse from(PlanBulkResultDto r) {
    return new PlanBulkResponse(
        r.items().stream().map(PlanResponse::from).toList(), r.created(), r.updated());
  }
}
