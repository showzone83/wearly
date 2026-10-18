package app.wearly.plan.domain;

import java.time.Instant;
import java.util.List;

/** 예정 코디 하나 (회원 소유). clientId는 처음 정한 뒤 바꾸지 않는다 */
public class Plan {

  private final Long id;
  private final long userId;
  private final String clientId;
  private PlanInput input;
  private final Instant createdAt;
  private final Instant updatedAt;

  public Plan(
      Long id,
      long userId,
      String clientId,
      PlanInput input,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.userId = userId;
    this.clientId = clientId;
    this.input = input;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Plan create(long userId, String clientId, PlanInput input) {
    return new Plan(null, userId, clientId, input, null, null);
  }

  /** 모든 값을 바꾼다 (PATCH 병합 결과·같은 clientId로 다시 올린 값) */
  public void apply(PlanInput input) {
    this.input = input;
  }

  /** 지금 값을 검증 전 입력 모양으로 (PATCH 병합의 바탕) */
  public PlanDraft toDraft() {
    PlanInput in = input;
    PlanPlace p = in.place();
    PlanWeather w = in.weather();
    return new PlanDraft(
        clientId,
        in.date().toString(),
        in.timeOfDay(),
        in.title(),
        in.occasion(),
        List.copyOf(in.concepts()),
        in.memo(),
        p == null ? null : new PlanDraft.Place(p.regionCode(), p.label(), p.lat(), p.lon()),
        new PlanDraft.Weather(w.temp(), w.min(), w.max(), w.rainProb(), w.cond(), w.manual()),
        w.source(),
        w.checkedAt() == null ? null : w.checkedAt().toString(),
        in.forecastReady(),
        in.outfit());
  }

  public Long id() {
    return id;
  }

  public long userId() {
    return userId;
  }

  public String clientId() {
    return clientId;
  }

  public PlanInput input() {
    return input;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
