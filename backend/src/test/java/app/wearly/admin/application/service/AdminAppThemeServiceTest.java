package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 테마 변경: 이메일 붙이기와 변경 이력 (가짜 포트, Spring 없이) */
class AdminAppThemeServiceTest {

  record Audit(
      Long adminId,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> before,
      Map<String, Object> after) {}

  static final Instant AT = Instant.parse("2026-10-17T09:00:00Z");

  final FakeUsers users = new FakeUsers();
  final List<Audit> audits = new ArrayList<>();
  ThemeSettingDto current = new ThemeSettingDto("nyang", null, null);

  final AppSettingUseCase settings =
      new AppSettingUseCase() {
        @Override
        public AppConfigDto config() {
          throw new UnsupportedOperationException();
        }

        @Override
        public ThemeSettingDto theme() {
          return current;
        }

        @Override
        public ThemeChangeDto changeTheme(long adminId, String themeId) {
          ThemeSettingDto before = current;
          if (before.themeId().equals(themeId)) {
            return new ThemeChangeDto(before, before, false);
          }
          current = new ThemeSettingDto(themeId, AT, adminId);
          return new ThemeChangeDto(before, current, true);
        }
      };

  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) ->
          audits.add(new Audit(adminId, action, targetType, targetId, before, after));

  final AdminAppThemeService service = new AdminAppThemeService(settings, users, audit);

  @Test
  void 처음_상태는_바꾼_시각과_관리자가_null이다() {
    assertThat(service.theme()).isEqualTo(new AdminThemeDto("nyang", null, null));
  }

  @Test
  void 바꾸면_관리자_이메일을_붙이고_변경_이력을_남긴다() {
    UserAccountDto admin =
        users.add("admin@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);

    AdminThemeDto after = service.changeTheme(admin.id(), "closeta");

    assertThat(after).isEqualTo(new AdminThemeDto("closeta", AT, "admin@wearly.test"));
    assertThat(audits)
        .containsExactly(
            new Audit(
                admin.id(),
                "APP_THEME_UPDATE",
                "APP_SETTING",
                "theme",
                Map.of("themeId", "nyang"),
                Map.of("themeId", "closeta")));
  }

  @Test
  void 같은_테마면_이력을_남기지_않고_탈퇴한_관리자는_null로_보인다() {
    current = new ThemeSettingDto("closeta", AT, 404L);

    assertThat(service.changeTheme(1L, "closeta"))
        .isEqualTo(new AdminThemeDto("closeta", AT, null));
    assertThat(audits).isEmpty();
  }
}
