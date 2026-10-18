package app.wearly.admin.adapter.in.web.response;

import app.wearly.common.web.Ids;

/** POST /admin/weather/refresh (202): 시작한 실행 ID. 진행은 GET runs/{runId}로 본다 */
public record WeatherRefreshResponse(String runId) {

  public static WeatherRefreshResponse of(long runId) {
    return new WeatherRefreshResponse(Ids.format(runId));
  }
}
