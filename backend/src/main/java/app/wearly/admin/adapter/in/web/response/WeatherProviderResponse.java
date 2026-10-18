package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.WeatherProviderDto;

/**
 * 날씨 공급자 (17 문서 6장). 키 값은 없고 키 이름과 상태(CONFIGURED·MISSING·MOCK·UNSUPPORTED)만 준다. status는 OK · OPEN ·
 * HALF_OPEN · KEY_INVALID
 */
public record WeatherProviderResponse(
    String code,
    String displayName,
    boolean enabled,
    int priority,
    String keyName,
    String keyState,
    String status,
    int consecutiveFailures,
    String circuitOpenUntil,
    String lastSuccessAt,
    String lastFailureAt,
    String lastError,
    long monthUsage,
    Integer monthlyQuota,
    int maxForecastDays,
    int callsPerFetch,
    boolean usable,
    String updatedBy,
    String updatedAt) {

  public static WeatherProviderResponse from(WeatherProviderDto p) {
    return new WeatherProviderResponse(
        p.code(),
        p.displayName(),
        p.enabled(),
        p.priority(),
        p.keyName(),
        p.keyState(),
        p.status(),
        p.consecutiveFailures(),
        Times.iso(p.circuitOpenUntil()),
        Times.iso(p.lastSuccessAt()),
        Times.iso(p.lastFailureAt()),
        p.lastError(),
        p.monthUsage(),
        p.monthlyQuota(),
        p.maxForecastDays(),
        p.callsPerFetch(),
        p.usable(),
        Ids.format(p.updatedBy()),
        Times.iso(p.updatedAt()));
  }
}
