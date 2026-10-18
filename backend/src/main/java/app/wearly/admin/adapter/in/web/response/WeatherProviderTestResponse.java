package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.weather.application.port.in.dto.ProviderTestResultDto;
import java.util.List;
import java.util.Map;

/** POST /admin/weather/providers/{code}/test (17 문서 6장). 실패면 current는 null, days는 빈 배열 */
public record WeatherProviderTestResponse(
    String runId,
    String providerCode,
    String regionCode,
    boolean success,
    String errorCode,
    String errorMessage,
    Integer httpStatus,
    long durationMs,
    String locationName,
    WeatherCurrentResponse current,
    List<WeatherDayResponse> days,
    Map<String, Object> raw) {

  public static WeatherProviderTestResponse from(ProviderTestResultDto r) {
    return new WeatherProviderTestResponse(
        Ids.format(r.runId()),
        r.providerCode(),
        r.regionCode(),
        r.success(),
        r.errorCode(),
        r.errorMessage(),
        r.httpStatus(),
        r.durationMs(),
        r.locationName(),
        WeatherCurrentResponse.from(r.current()),
        WeatherDayResponse.from(r.days()),
        r.raw());
  }
}
