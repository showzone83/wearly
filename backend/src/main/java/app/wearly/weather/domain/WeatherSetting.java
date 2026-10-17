package app.wearly.weather.domain;

import app.wearly.common.domain.FieldError;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 날씨 스케줄 설정 (tb_weather_setting 한 행, 17 문서 3장). 관리자가 바꾸면 다음 실행 시각 계산부터 바로 반영된다.
 *
 * @param failureThreshold 공급자를 차단하는 연속 실패 수
 * @param cooldownMinutes 차단 시간 (끝나면 한 지역으로 다시 시험한다)
 */
public record WeatherSetting(
    boolean enabled,
    int intervalMinutes,
    int forecastDays,
    int failureThreshold,
    int cooldownMinutes,
    Long updatedBy,
    Instant updatedAt) {

  public static final int MIN_INTERVAL = 10;
  public static final int MAX_INTERVAL = 1440;
  public static final int MAX_FORECAST_DAYS = 14;
  public static final int MAX_THRESHOLD = 20;
  public static final int MAX_COOLDOWN = 1440;

  /** 저장 값을 못 읽을 때 쓰는 값 (V11 마이그레이션 기본값과 같다) */
  public static WeatherSetting defaults() {
    return new WeatherSetting(true, 30, 3, 3, 15, null, null);
  }

  /** 범위 검증. 필드 이름은 관리자 API JSON 이름 */
  public static List<FieldError> validate(
      int intervalMinutes, int forecastDays, int failureThreshold, int cooldownMinutes) {
    List<FieldError> errors = new ArrayList<>();
    range(errors, "intervalMinutes", intervalMinutes, MIN_INTERVAL, MAX_INTERVAL);
    range(errors, "forecastDays", forecastDays, 1, MAX_FORECAST_DAYS);
    range(errors, "failureThreshold", failureThreshold, 1, MAX_THRESHOLD);
    range(errors, "cooldownMinutes", cooldownMinutes, 1, MAX_COOLDOWN);
    return errors;
  }

  public Duration interval() {
    return Duration.ofMinutes(intervalMinutes);
  }

  public Duration cooldown() {
    return Duration.ofMinutes(cooldownMinutes);
  }

  /** 직전 실행이 끝난 시각 + 주기 */
  public Instant nextRunAfter(Instant lastFinished) {
    return lastFinished.plus(interval());
  }

  /** 저장 시각이 주기의 3배보다 오래되면 앱에 "업데이트 지연"을 알린다 */
  public boolean stale(Instant fetchedAt, Instant now) {
    return fetchedAt == null || fetchedAt.plus(interval().multipliedBy(3)).isBefore(now);
  }

  /** 공급자가 줄 수 있는 일수와 설정 중 작은 쪽 */
  public int daysFor(WeatherProvider provider) {
    return Math.min(forecastDays, provider.maxForecastDays());
  }

  private static void range(List<FieldError> errors, String field, int v, int min, int max) {
    if (v < min || v > max) {
      errors.add(new FieldError(field, min + "~" + max + " 사이여야 해요."));
    }
  }
}
