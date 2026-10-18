package app.wearly.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.wearly.admin.application.port.in.dto.AdminThemeDto;
import app.wearly.admin.application.port.out.RecordAuditPort;
import app.wearly.appsetting.application.port.in.AppSettingUseCase;
import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeSettingDto;
import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;
import app.wearly.support.FakeUsers;
import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 관리자 테마·계절 모드 변경: 이메일 붙이기와 변경 이력 (가짜 포트, Spring 없이) */
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
  ThemeSettingDto current = new ThemeSettingDto("nyang", "OFF", null, null);

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
        public ThemeChangeDto changeTheme(long adminId, ThemeUpdateDto update) {
          ThemeSettingDto before = current;
          String themeId = update.themeId() == null ? before.themeId() : update.themeId();
          String mode = update.seasonMode() == null ? before.seasonMode() : update.seasonMode();
          if (before.themeId().equals(themeId) && before.seasonMode().equals(mode)) {
            return new ThemeChangeDto(before, before, false);
          }
          current = new ThemeSettingDto(themeId, mode, AT, adminId);
          return new ThemeChangeDto(before, current, true);
        }
      };

  final RecordAuditPort audit =
      (adminId, action, targetType, targetId, before, after) ->
          audits.add(new Audit(adminId, action, targetType, targetId, before, after));

  final AdminAppThemeService service = new AdminAppThemeService(settings, users, audit);

  @Test
  void 처음_상태는_바꾼_시각과_관리자가_null이다() {
    assertThat(service.theme()).isEqualTo(new AdminThemeDto("nyang", "OFF", null, null));
  }

  @Test
  void 테마만_바꾸면_이력에_themeId만_남는다() {
    UserAccountDto admin =
        users.add("admin@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);

    AdminThemeDto after = service.changeTheme(admin.id(), new ThemeUpdateDto("closeta", null));

    assertThat(after).isEqualTo(new AdminThemeDto("closeta", "OFF", AT, "admin@wearly.test"));
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
  void 계절_모드만_바꾸면_이력에_seasonMode만_남고_둘_다_바꾸면_둘_다_남는다() {
    UserAccountDto admin =
        users.add("admin@wearly.test", UserAccountDto.ROLE_ADMIN, UserAccountDto.ACTIVE);

    service.changeTheme(admin.id(), new ThemeUpdateDto(null, "AUTO"));
    service.changeTheme(admin.id(), new ThemeUpdateDto("closeta", "WINTER"));

    assertThat(audits)
        .extracting(Audit::before, Audit::after)
        .containsExactly(
            tuple(Map.of("seasonMode", "OFF"), Map.of("seasonMode", "AUTO")),
            tuple(
                Map.of("themeId", "nyang", "seasonMode", "AUTO"),
                Map.of("themeId", "closeta", "seasonMode", "WINTER")));
    assertThat(service.theme().seasonMode()).isEqualTo("WINTER");
  }

  @Test
  void 같은_값이면_이력을_남기지_않고_탈퇴한_관리자는_null로_보인다() {
    current = new ThemeSettingDto("closeta", "FALL", AT, 404L);

    assertThat(service.changeTheme(1L, new ThemeUpdateDto("closeta", "FALL")))
        .isEqualTo(new AdminThemeDto("closeta", "FALL", AT, null));
    assertThat(audits).isEmpty();
  }
}
