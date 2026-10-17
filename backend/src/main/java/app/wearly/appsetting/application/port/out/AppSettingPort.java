package app.wearly.appsetting.application.port.out;

import app.wearly.appsetting.domain.ThemeSetting;
import java.util.Optional;

/** 앱 설정 읽기·쓰기 (JPA, tb_app_setting) */
public interface AppSettingPort {

  /** theme 행. 없으면 빈 값 */
  Optional<ThemeSetting> findTheme();

  /** theme 행을 넣거나 바꾸고 저장된 모습을 돌려준다 */
  ThemeSetting saveTheme(ThemeSetting setting);
}
