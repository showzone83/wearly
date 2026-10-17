package app.wearly.appsetting.application.port.in;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;

/**
 * 앱 전체 설정 (12 문서 3장). 공개 조회(GET /app/config)는 웹 어댑터가, 관리자 조회·변경(/admin/app/theme)은 admin 컨텍스트가 이
 * 포트로 부른다. 변경 이력은 부르는 쪽(admin)이 같은 트랜잭션에 남긴다.
 */
public interface AppSettingUseCase {

  /** 공개 설정: { themeId, version } */
  AppConfigDto config();

  ThemeSettingDto theme();

  /**
   * 전체 사용자의 테마를 바꾼다. 허용 값(nyang · closeta)이 아니면 400 VALIDATION_FAILED(themeId). 지금 테마와 같으면 저장하지 않고
   * changed = false
   */
  ThemeChangeDto changeTheme(long adminId, String themeId);
}
