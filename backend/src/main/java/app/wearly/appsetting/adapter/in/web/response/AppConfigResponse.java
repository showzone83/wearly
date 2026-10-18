package app.wearly.appsetting.adapter.in.web.response;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;

/**
 * GET /app/config (12 문서 3장, 이슈 #6): { themeId, seasonMode, version }. AUTO일 때 지금 계절은 앱이
 * 날짜(Asia/Seoul)로 계산한다(서버가 계절을 주면 날짜가 바뀔 때 ETag가 맞지 않게 된다)
 */
public record AppConfigResponse(String themeId, String seasonMode, String version) {

  public static AppConfigResponse from(AppConfigDto d) {
    return new AppConfigResponse(d.themeId(), d.seasonMode(), d.version());
  }
}
