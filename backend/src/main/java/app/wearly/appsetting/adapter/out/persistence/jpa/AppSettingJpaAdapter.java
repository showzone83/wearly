package app.wearly.appsetting.adapter.out.persistence.jpa;

import app.wearly.appsetting.application.port.out.AppSettingPort;
import app.wearly.appsetting.domain.AppThemes;
import app.wearly.appsetting.domain.SeasonMode;
import app.wearly.appsetting.domain.ThemeSetting;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AppSettingPort 구현 (JPA). theme 행의 JSON 값 {"themeId": ..., "seasonMode": ...} ↔ ThemeSetting.
 * seasonMode가 없는 옛 행(V8 초기값 {"themeId":"nyang"})은 OFF로 읽는다(마이그레이션 없음)
 */
@Component
class AppSettingJpaAdapter implements AppSettingPort {

  static final String THEME_KEY = "theme";
  static final String THEME_ID = "themeId";
  static final String SEASON_MODE = "seasonMode";

  private final AppSettingJpaRepository settings;

  AppSettingJpaAdapter(AppSettingJpaRepository settings) {
    this.settings = settings;
  }

  @Override
  public Optional<ThemeSetting> findTheme() {
    return settings.findById(THEME_KEY).map(AppSettingJpaAdapter::toTheme);
  }

  @Override
  public ThemeSetting saveTheme(ThemeSetting setting) {
    AppSettingEntity e =
        settings.findById(THEME_KEY).orElseGet(() -> new AppSettingEntity(THEME_KEY));
    Map<String, Object> value = new LinkedHashMap<>();
    value.put(THEME_ID, setting.themeId());
    value.put(SEASON_MODE, setting.seasonMode().name());
    e.update(value, setting.updatedBy(), setting.updatedAt());
    return toTheme(settings.saveAndFlush(e));
  }

  private static ThemeSetting toTheme(AppSettingEntity e) {
    Map<String, Object> value = e.getValue() == null ? Map.of() : e.getValue();
    Object themeId = value.get(THEME_ID);
    return new ThemeSetting(
        themeId instanceof String s ? s : AppThemes.DEFAULT,
        SeasonMode.parseOrDefault(value.get(SEASON_MODE)),
        e.getUpdatedAt(),
        e.getUpdatedBy());
  }
}
