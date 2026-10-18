package app.wearly.admin.application.service;

import app.wearly.admin.application.port.in.AdminAppThemeUseCase;
import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.admin.domain.AuditTargets;
import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 테마·계절 모드 조회·변경 (12 문서 3장, 이슈 #6). 실제 저장은 appsetting 컨텍스트의 유스케이스가 하고, 여기서는 관리자 이메일을 붙이고 변경 이력을
 * 같은 트랜잭션에 남긴다.
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
  public AdminThemeDto changeTheme(long adminId, ThemeUpdateDto update) {
    ThemeChangeDto change = settings.changeTheme(adminId, update);
    if (change.changed()) {
      // 바뀐 필드만 남긴다: 테마만 바꾸면 예전처럼 {"themeId": ...}
      Map<String, Object> before = new LinkedHashMap<>();
      Map<String, Object> after = new LinkedHashMap<>();
      if (!Objects.equals(change.before().themeId(), change.after().themeId())) {
        before.put("themeId", change.before().themeId());
        after.put("themeId", change.after().themeId());
      }
      if (!Objects.equals(change.before().seasonMode(), change.after().seasonMode())) {
        before.put("seasonMode", change.before().seasonMode());
        after.put("seasonMode", change.after().seasonMode());
      }
      audit.record(adminId, ACTION, AuditTargets.APP_SETTING, TARGET_ID, before, after);
    }
    return view(change.after());
  }

  private AdminThemeDto view(ThemeSettingDto s) {
    String email =
        s.updatedBy() == null
            ? null
            : users.find(s.updatedBy()).map(UserAccountDto::email).orElse(null);
    return new AdminThemeDto(s.themeId(), s.seasonMode(), s.updatedAt(), email);
  }
}
