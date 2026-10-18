package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.common.web.Times;

/**
 * GET·PUT /admin/app/theme (12 문서 3장, 이슈 #6): { themeId, seasonMode, updatedAt, updatedBy }. 바꾼 적이
 * 없으면 updatedAt·updatedBy는 null
 */
public record AdminThemeResponse(
    String themeId, String seasonMode, String updatedAt, String updatedBy) {

  public static AdminThemeResponse from(AdminThemeDto d) {
    return new AdminThemeResponse(
        d.themeId(), d.seasonMode(), Times.iso(d.updatedAt()), d.updatedBy());
  }
}
