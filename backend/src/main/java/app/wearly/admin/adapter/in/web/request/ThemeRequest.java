package app.wearly.admin.adapter.in.web.request;

import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;

/**
 * PUT /admin/app/theme 본문 (12 문서 3장, 이슈 #6). 보낸 값만 바꾼다(빠진 값은 그대로). 허용 값 검증은 유스케이스가 한다
 *
 * @param themeId 테마 ID (nyang · closeta)
 * @param seasonMode 계절 테마 모드 (OFF · AUTO · SPRING · SUMMER · FALL · WINTER)
 */
public record ThemeRequest(String themeId, String seasonMode) {

  public ThemeUpdateDto toDto() {
    return new ThemeUpdateDto(themeId, seasonMode);
  }
}
