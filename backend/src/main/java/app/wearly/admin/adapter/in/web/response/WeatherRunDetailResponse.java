package app.wearly.admin.adapter.in.web.response;

import app.wearly.weather.application.port.in.dto.RefreshRunDetailDto;
import java.util.List;

/** GET /admin/weather/runs/{runId}: 실행 + 그 실행의 호출 (호출한 순서) */
public record WeatherRunDetailResponse(WeatherRunResponse run, List<WeatherCallResponse> calls) {

  public static WeatherRunDetailResponse from(RefreshRunDetailDto d) {
    return new WeatherRunDetailResponse(
        WeatherRunResponse.from(d.run()),
        d.calls().stream().map(WeatherCallResponse::from).toList());
  }
}
