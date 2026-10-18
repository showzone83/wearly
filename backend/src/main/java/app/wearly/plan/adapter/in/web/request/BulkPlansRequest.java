package app.wearly.plan.adapter.in.web.request;

import app.wearly.plan.application.port.in.dto.PlanInputDto;
import java.util.ArrayList;
import java.util.List;

/** POST /plans/bulk { items: PlanRequest[] } (최대 100개, clientId 필수). 개수·필수 검사는 유스케이스가 한다 */
public record BulkPlansRequest(List<PlanRequest> items) {

  /** items가 없으면 null (유스케이스가 REQUIRED로 거절한다) */
  public List<PlanInputDto> toDto() {
    if (items == null) {
      return null;
    }
    List<PlanInputDto> out = new ArrayList<>(items.size());
    for (PlanRequest r : items) {
      out.add(r == null ? null : r.toDto());
    }
    return out;
  }
}
