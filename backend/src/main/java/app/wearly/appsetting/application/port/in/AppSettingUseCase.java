package app.wearly.appsetting.application.port.in;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;

/**
 * 앱 전체 설정 (12 문서 3장). 공개 조회(GET /app/config)는 웹 어댑터가, 관리자 조회·변경(/admin/app/theme)은 admin 컨텍스트가 이
 * 포트로 부른다. 변경 이력은 부르는 쪽(admin)이 같은 트랜잭션에 남긴다.
 */
public interface AppSettingUseCase {

  /** 공개 설정: { themeId, seasonMode, version } */
  AppConfigDto config();

  ThemeSettingDto theme();

  /**
   * 전체 사용자의 테마·계절 모드를 바꾼다. 보낸 값만 바꾸고, 둘 다 없으면 400 VALIDATION_FAILED(themeId). 허용 값이 아니면 400
   * VALIDATION_FAILED(themeId · seasonMode). 지금 값과 같으면 저장하지 않고 changed = false
   */
  ThemeChangeDto changeTheme(long adminId, ThemeUpdateDto update);
}
