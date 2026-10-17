package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminAppThemeUseCase;
import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 테마 조회·변경 (12 문서 3장). 실제 저장은 appsetting 컨텍스트의 유스케이스가 하고, 여기서는 관리자 이메일을 붙이고 변경 이력을 같은 트랜잭션에
 * 남긴다.
 */
@Service
public class AdminAppThemeService implements AdminAppThemeUseCase {

  static final String ACTION = "APP_THEME_UPDATE";
  static final String TARGET_ID = "theme";

  private final AppSettingUseCase settings;
  private final UserUseCase users;
  private final RecordAuditPort audit;

  public AdminAppThemeService(
      AppSettingUseCase settings, UserUseCase users, RecordAuditPort audit) {
    this.settings = settings;
    this.users = users;
    this.audit = audit;
  }

  @Override
  @Transactional(readOnly = true)
  public AdminThemeDto theme() {
    return view(settings.theme());
  }

  @Override
  @Transactional
  public AdminThemeDto changeTheme(long adminId, String themeId) {
    ThemeChangeDto change = settings.changeTheme(adminId, themeId);
    if (change.changed()) {
      audit.record(
          adminId,
          ACTION,
          AuditTargets.APP_SETTING,
          TARGET_ID,
          Map.of("themeId", change.before().themeId()),
          Map.of("themeId", change.after().themeId()));
    }
    return view(change.after());
  }

  private AdminThemeDto view(ThemeSettingDto s) {
    String email =
        s.updatedBy() == null
            ? null
            : users.find(s.updatedBy()).map(UserAccountDto::email).orElse(null);
    return new AdminThemeDto(s.themeId(), s.updatedAt(), email);
  }
}
