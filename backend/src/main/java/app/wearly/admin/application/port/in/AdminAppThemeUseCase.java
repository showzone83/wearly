package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;

/** 관리자 앱 설정 > 테마 (12 문서 3·6장, 이슈 #6). 전체 사용자의 테마와 계절 모드를 바꾼다 */
public interface AdminAppThemeUseCase {

  AdminThemeDto theme();

  /**
   * 보낸 값(themeId · seasonMode)만 바꾼다. 둘 다 없으면 400 VALIDATION_FAILED(themeId), 허용 값이 아니면 400
   * VALIDATION_FAILED(그 필드). 바뀌면 변경 이력 APP_THEME_UPDATE를 같은 트랜잭션에 남긴다(before·after에는 바뀐 필드만). 지금 값과
   * 같으면 아무것도 바꾸지 않고 지금 상태를 돌려준다(이력 없음)
   */
  AdminThemeDto changeTheme(long adminId, ThemeUpdateDto update);
}
