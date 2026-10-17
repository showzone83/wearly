package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.RefreshRunDto;

/** 날씨 실행 한 번 (17 문서 6장) */
public record WeatherRunResponse(
    String runId,
    String trigger,
    String requestedBy,
    String requestedByEmail,
    String regionCode,
    String status,
    String skipReason,
    int successCount,
    int failCount,
    int fallbackCount,
    String startedAt,
    String finishedAt) {

  public static WeatherRunResponse from(RefreshRunDto r) {
    return r == null
        ? null
        : new WeatherRunResponse(
            Ids.format(r.runId()),
            r.trigger(),
            Ids.format(r.requestedBy()),
            r.requestedByEmail(),
            r.regionCode(),
            r.status(),
            r.skipReason(),
            r.successCount(),
            r.failCount(),
            r.fallbackCount(),
            Times.iso(r.startedAt()),
            Times.iso(r.finishedAt()));
  }
}
