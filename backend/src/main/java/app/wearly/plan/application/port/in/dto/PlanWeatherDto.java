package app.wearly.plan.application.port.in.dto;

import app.wearly.plan.domain.PlanDraft;
import app.wearly.plan.domain.PlanWeather;

/** 예정일 날씨 (앱 Weather 모양, 입력·출력 같은 모양). 출처·확인 시각은 PlanDto에 따로 있다 */
public record PlanWeatherDto(
    Double temp, Double min, Double max, Double rainProb, String cond, Boolean manual) {

  public PlanDraft.Weather toDraft() {
    return new PlanDraft.Weather(temp, min, max, rainProb, cond, manual);
  }

  public static PlanWeatherDto from(PlanWeather w) {
    return new PlanWeatherDto(w.temp(), w.min(), w.max(), w.rainProb(), w.cond(), w.manual());
  }
}
