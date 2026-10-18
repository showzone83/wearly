package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.weather.application.port.in.dto.WeatherSnapshotDto;
import java.util.List;
import java.util.Map;

/**
 * 저장된 날씨 한 지역 (17 문서 6장). 한 번도 받지 못했으면 current·fetchedAt이 null. raw는 상세(GET snapshots/{region})에만
 * 있고 목록에서는 null이다.
 */
public record WeatherSnapshotResponse(
    String regionCode,
    String regionLabel,
    boolean regionActive,
    String providerCode,
    WeatherCurrentResponse current,
    List<WeatherDayResponse> days,
    String locationName,
    String fetchedAt,
    boolean stale,
    String lastError,
    String lastErrorAt,
    Map<String, Object> raw) {

  public static WeatherSnapshotResponse from(WeatherSnapshotDto s) {
    return new WeatherSnapshotResponse(
        s.regionCode(),
        s.regionLabel(),
        s.regionActive(),
        s.providerCode(),
        WeatherCurrentResponse.from(s.current()),
        WeatherDayResponse.from(s.days()),
        s.locationName(),
        Times.iso(s.fetchedAt()),
        s.stale(),
        s.lastError(),
        Times.iso(s.lastErrorAt()),
        s.raw());
  }
}
