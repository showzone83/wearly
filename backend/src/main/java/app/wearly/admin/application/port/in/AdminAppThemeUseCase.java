package app.wearly.admin.application.port.in;

import app.wearly.admin.application.port.in.dto.AdminThemeDto;

/** 관리자 앱 설정 > 테마 (12 문서 3·6장). 전체 사용자의 테마를 바꾼다 */
public interface AdminAppThemeUseCase {

  AdminThemeDto theme();

  /**
   * 허용 값(nyang · closeta)이 아니면 400 VALIDATION_FAILED(themeId). 바뀌면 변경 이력 APP_THEME_UPDATE를 같은 트랜잭션에
   * 남긴다. 지금 테마와 같으면 아무것도 바꾸지 않고 지금 상태를 돌려준다(이력 없음)
   */
  AdminThemeDto changeTheme(long adminId, String themeId);
}
