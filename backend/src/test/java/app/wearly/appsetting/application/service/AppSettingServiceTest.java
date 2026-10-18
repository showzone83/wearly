package app.wearly.appsetting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.in.dto.ThemeUpdateDto;
import app.wearly.appsetting.application.port.out.AppSettingPort;
import app.wearly.appsetting.domain.SeasonMode;
import app.wearly.appsetting.domain.ThemeSetting;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.BusinessException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 앱 설정 조회·테마·계절 모드 변경 (가짜 포트, Spring 없이) */
class AppSettingServiceTest {

  static final Instant NOW = Instant.parse("2026-10-17T09:00:00.250Z");

  ThemeSetting row;
  int saves;

  final AppSettingPort port =
      new AppSettingPort() {
        @Override
        public Optional<ThemeSetting> findTheme() {
          return Optional.ofNullable(row);
        }

        @Override
        public ThemeSetting saveTheme(ThemeSetting setting) {
          saves++;
          row = setting;
          return setting;
        }
      };

  final AppSettingService service = new AppSettingService(port, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void 설정_행이_없어도_기본_테마와_계절_OFF를_돌려준다() {
    assertThat(service.config()).isEqualTo(new AppConfigDto("nyang", "OFF", "initial"));
    assertThat(service.theme().updatedAt()).isNull();
    assertThat(service.theme().seasonMode()).isEqualTo("OFF");
  }

  @Test
  void 테마를_바꾸면_저장하고_전후를_돌려준다() {
    row = new ThemeSetting("nyang", SeasonMode.OFF, null, null);

    ThemeChangeDto change = service.changeTheme(5L, new ThemeUpdateDto("closeta", null));

    assertThat(change.changed()).isTrue();
    assertThat(change.before().themeId()).isEqualTo("nyang");
    assertThat(change.after().themeId()).isEqualTo("closeta");
    assertThat(change.after().seasonMode()).isEqualTo("OFF");
    assertThat(change.after().updatedAt()).isEqualTo(NOW);
    assertThat(change.after().updatedBy()).isEqualTo(5L);
    assertThat(service.config())
        .isEqualTo(new AppConfigDto("closeta", "OFF", "2026-10-17T09:00:00.250Z"));
    assertThat(saves).isEqualTo(1);
  }

  @Test
  void 계절_모드만_바꾸면_테마는_그대로이고_version이_바뀐다() {
    row = new ThemeSetting("closeta", SeasonMode.OFF, Instant.EPOCH, 1L);

    ThemeChangeDto change = service.changeTheme(5L, new ThemeUpdateDto(null, "AUTO"));

    assertThat(change.changed()).isTrue();
    assertThat(change.before().seasonMode()).isEqualTo("OFF");
    assertThat(change.after().seasonMode()).isEqualTo("AUTO");
    assertThat(change.after().themeId()).isEqualTo("closeta");
    assertThat(service.config())
        .isEqualTo(new AppConfigDto("closeta", "AUTO", "2026-10-17T09:00:00.250Z"));
  }

  @Test
  void 같은_값으로_바꾸면_저장하지_않는다() {
    row = new ThemeSetting("closeta", SeasonMode.FALL, Instant.EPOCH, 1L);

    ThemeChangeDto change = service.changeTheme(5L, new ThemeUpdateDto("closeta", "FALL"));

    assertThat(change.changed()).isFalse();
    assertThat(change.after().updatedBy()).isEqualTo(1L);
    assertThat(service.changeTheme(5L, new ThemeUpdateDto(null, "FALL")).changed()).isFalse();
    assertThat(saves).isZero();
  }

  @Test
  void 허용하지_않는_값은_400_필드별() {
    assertThatThrownBy(() -> service.changeTheme(5L, new ThemeUpdateDto("dark", "autumn")))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.fieldErrors())
                  .extracting(FieldError::field)
                  .containsExactly("themeId", "seasonMode");
            });
    assertThatThrownBy(() -> service.changeTheme(5L, null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.fieldErrors())
                    .extracting(FieldError::field)
                    .containsExactly("themeId"));
    assertThat(saves).isZero();
  }
}
