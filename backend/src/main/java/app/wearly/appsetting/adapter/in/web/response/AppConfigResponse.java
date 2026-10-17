package app.wearly.appsetting.adapter.in.web.response;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;

/** GET /app/config (12 문서 3장): { themeId, version } */
public record AppConfigResponse(String themeId, String version) {

  public static AppConfigResponse from(AppConfigDto d) {
    return new AppConfigResponse(d.themeId(), d.version());
  }
}
