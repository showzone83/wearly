package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.request.ThemeRequest;
import app.wearly.admin.adapter.in.web.response.AdminThemeResponse;
import app.wearly.admin.application.port.in.AdminAppThemeUseCase;
import app.wearly.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 앱 설정 API (12 문서 3장). ADMIN 전용 */
@RestController
@RequestMapping("/api/v1/admin/app")
public class AdminAppController {

  private final AdminAppThemeUseCase themes;

  public AdminAppController(AdminAppThemeUseCase themes) {
    this.themes = themes;
  }

  @GetMapping("/theme")
  public AdminThemeResponse theme() {
    return AdminThemeResponse.from(themes.theme());
  }

  /** 보낸 값(themeId · seasonMode)만 바꾼다. 본문이 없거나 둘 다 없으면 400 VALIDATION_FAILED(themeId) */
  @PutMapping("/theme")
  public AdminThemeResponse changeTheme(
      @CurrentUser long adminId, @RequestBody(required = false) ThemeRequest request) {
    return AdminThemeResponse.from(
        themes.changeTheme(adminId, request == null ? null : request.toDto()));
  }
}
