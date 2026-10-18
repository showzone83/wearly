package app.wearly.plan.adapter.in.web.response;

import app.wearly.plan.application.port.in.dto.PlanPlaceDto;

/** 예정 장소 { regionCode, label, lat, lon } (regionCode는 null일 수 있다) */
public record PlanPlaceResponse(String regionCode, String label, Double lat, Double lon) {

  public static PlanPlaceResponse from(PlanPlaceDto p) {
    return p == null ? null : new PlanPlaceResponse(p.regionCode(), p.label(), p.lat(), p.lon());
  }
}
