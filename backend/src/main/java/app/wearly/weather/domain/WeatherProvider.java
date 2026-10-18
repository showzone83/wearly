package app.wearly.weather.domain;

import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 날씨 공급자 한 곳 (tb_weather_provider). 설정 칸은 관리자가, health는 실행이 바꾼다.
 *
 * @param keyName 서버 설정의 키 이름 (wearly.weather.keys.&lt;keyName&gt;). 키 값은 DB에 없다
 * @param monthlyQuota 이번 달(한국 시간) 호출 한도. null이면 없음
 */
public record WeatherProvider(
    String code,
    String displayName,
    boolean enabled,
    int priority,
    String keyName,
    Integer monthlyQuota,
    int maxForecastDays,
    ProviderHealth health,
    Long updatedBy,
    Instant updatedAt) {

  public static final int MAX_PRIORITY = 10000;

  /**
   * 이번 실행에서 부를 수 있는지. 키가 있고, 켜져 있고, 차단 중이 아니고, 이번 달 한도에 이번 호출이 들어가야 한다.
   *
   * @param used 이번 달 지금까지 호출 수 (실패 포함)
   * @param calls 한 지역을 받는 데 드는 호출 수
   */
  public boolean usable(boolean keyConfigured, Instant now, long used, int calls) {
    ProviderStatus s = health.status(now);
    return enabled
        && keyConfigured
        && s != ProviderStatus.OPEN
        && s != ProviderStatus.KEY_INVALID
        && !overQuota(used, calls);
  }

  public boolean overQuota(long used, int calls) {
    return monthlyQuota != null && used + calls > monthlyQuota;
  }

  public WeatherProvider withHealth(ProviderHealth h) {
    return new WeatherProvider(
        code,
        displayName,
        enabled,
        priority,
        keyName,
        monthlyQuota,
        maxForecastDays,
        h,
        updatedBy,
        updatedAt);
  }

  /** 관리자 설정 변경. 범위를 벗어나면 필드 오류를 모은다 */
  public static List<FieldError> validate(int priority, Integer monthlyQuota, int maxForecastDays) {
    List<FieldError> errors = new ArrayList<>();
    if (priority < 0 || priority > MAX_PRIORITY) {
      errors.add(new FieldError("priority", "0~" + MAX_PRIORITY + " 정수여야 해요."));
    }
    if (monthlyQuota != null && monthlyQuota < 0) {
      errors.add(new FieldError("monthlyQuota", "0 이상이거나 비워야 해요."));
    }
    if (maxForecastDays < 1 || maxForecastDays > WeatherSetting.MAX_FORECAST_DAYS) {
      errors.add(
          new FieldError("maxForecastDays", "1~" + WeatherSetting.MAX_FORECAST_DAYS + " 사이여야 해요."));
    }
    return errors;
  }

  public WeatherProvider configured(
      boolean enabled,
      int priority,
      Integer monthlyQuota,
      int maxForecastDays,
      Long updatedBy,
      Instant updatedAt) {
    return new WeatherProvider(
        code,
        displayName,
        enabled,
        priority,
        keyName,
        monthlyQuota,
        maxForecastDays,
        health,
        updatedBy,
        updatedAt);
  }
}
