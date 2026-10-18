package app.wearly.appsetting.application.service;

import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.appsetting.application.port.out.AppSettingPort;
import app.wearly.appsetting.domain.AppThemes;
import app.wearly.appsetting.domain.ThemeSetting;
import app.wearly.common.error.BusinessException;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱 설정 조회·테마 변경 (JPA). 행이 한 개뿐이고 자주 바뀌지 않아 캐시하지 않는다 */
@Service
public class AppSettingService implements AppSettingUseCase {

  private final AppSettingPort settings;
  private final Clock clock;

  public AppSettingService(AppSettingPort settings, Clock clock) {
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public AppConfigDto config() {
    ThemeSetting theme = current();
    return new AppConfigDto(theme.themeId(), theme.version());
  }

  @Override
  @Transactional(readOnly = true)
  public ThemeSettingDto theme() {
    return ThemeSettingDto.from(current());
  }

  @Override
  @Transactional
  public ThemeChangeDto changeTheme(long adminId, String themeId) {
    BusinessException.throwIfAny(AppThemes.validate(themeId));
    ThemeSetting before = current();
    if (before.themeId().equals(themeId)) {
      ThemeSettingDto same = ThemeSettingDto.from(before);
      return new ThemeChangeDto(same, same, false);
    }
    ThemeSetting after = settings.saveTheme(before.changeTo(themeId, adminId, clock.instant()));
    return new ThemeChangeDto(ThemeSettingDto.from(before), ThemeSettingDto.from(after), true);
  }

  private ThemeSetting current() {
    return settings.findTheme().orElseGet(ThemeSetting::defaults);
  }
}
