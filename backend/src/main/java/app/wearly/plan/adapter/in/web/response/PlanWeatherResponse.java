package app.wearly.plan.adapter.in.web.response;

import app.wearly.plan.application.port.in.dto.PlanWeatherDto;

/** 예정일 날씨 (앱 Weather 모양). manual은 항상 true·false */
public record PlanWeatherResponse(
    Double temp, Double min, Double max, Double rainProb, String cond, boolean manual) {

  public static PlanWeatherResponse from(PlanWeatherDto w) {
    return new PlanWeatherResponse(
        w.temp(), w.min(), w.max(), w.rainProb(), w.cond(), Boolean.TRUE.equals(w.manual()));
  }
}
