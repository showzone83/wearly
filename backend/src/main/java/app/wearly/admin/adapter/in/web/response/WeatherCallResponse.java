package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.WeatherCallDto;

/** 날씨 공급자 호출 한 번 (17 문서 6장). 오류 문구에 키는 없다 */
public record WeatherCallResponse(
    String id,
    String runId,
    String trigger,
    String regionCode,
    String providerCode,
    String callKind,
    boolean fallback,
    Integer httpStatus,
    boolean success,
    String errorCode,
    String errorMessage,
    long durationMs,
    Integer responseBytes,
    String calledAt) {

  public static WeatherCallResponse from(WeatherCallDto c) {
    return new WeatherCallResponse(
        Ids.format(c.callId()),
        Ids.format(c.runId()),
        c.trigger(),
        c.regionCode(),
        c.providerCode(),
        c.callKind(),
        c.fallback(),
        c.httpStatus(),
        c.success(),
        c.errorCode(),
        c.errorMessage(),
        c.durationMs(),
        c.responseBytes(),
        Times.iso(c.calledAt()));
  }
}
