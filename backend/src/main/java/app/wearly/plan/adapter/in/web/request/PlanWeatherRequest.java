package app.wearly.plan.adapter.in.web.request;

import app.wearly.plan.application.port.in.dto.PlanWeatherDto;

/** 예정일 날씨 (앱 Weather 모양 { temp, min, max, rainProb, cond, manual? }) */
public record PlanWeatherRequest(
    Double temp, Double min, Double max, Double rainProb, String cond, Boolean manual) {

  public PlanWeatherDto toDto() {
    return new PlanWeatherDto(temp, min, max, rainProb, cond, manual);
  }
}
