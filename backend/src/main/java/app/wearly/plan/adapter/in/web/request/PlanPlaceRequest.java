package app.wearly.plan.adapter.in.web.request;

import app.wearly.plan.application.port.in.dto.PlanPlaceDto;

/** 예정 장소 { regionCode?, label, lat, lon } (19 문서 place 모양) */
public record PlanPlaceRequest(String regionCode, String label, Double lat, Double lon) {

  public PlanPlaceDto toDto() {
    return new PlanPlaceDto(regionCode, label, lat, lon);
  }
}
