package app.wearly.plan.application.port.in.dto;

import app.wearly.plan.domain.PlanDraft;
import app.wearly.plan.domain.PlanPlace;

/** 예정 장소 (입력·출력 같은 모양). regionCode는 REGION 코드 또는 null */
public record PlanPlaceDto(String regionCode, String label, Double lat, Double lon) {

  public PlanDraft.Place toDraft() {
    return new PlanDraft.Place(regionCode, label, lat, lon);
  }

  public static PlanPlaceDto from(PlanPlace p) {
    return p == null ? null : new PlanPlaceDto(p.regionCode(), p.label(), p.lat(), p.lon());
  }
}
