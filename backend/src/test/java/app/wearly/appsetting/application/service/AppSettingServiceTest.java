package app.wearly.appsetting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.appsetting.application.port.in.dto.AppConfigDto;
import app.wearly.appsetting.application.port.in.dto.ThemeChangeDto;
import app.wearly.appsetting.application.port.out.AppSettingPort;
import app.wearly.appsetting.domain.ThemeSetting;
import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 앱 설정 조회·테마 변경 (가짜 포트, Spring 없이) */
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
  void 설정_행이_없어도_기본_테마를_돌려준다() {
    assertThat(service.config()).isEqualTo(new AppConfigDto("nyang", "initial"));
    assertThat(service.theme().updatedAt()).isNull();
  }

  @Test
  void 테마를_바꾸면_저장하고_전후를_돌려준다() {
    row = new ThemeSetting("nyang", null, null);

    ThemeChangeDto change = service.changeTheme(5L, "closeta");

    assertThat(change.changed()).isTrue();
    assertThat(change.before().themeId()).isEqualTo("nyang");
    assertThat(change.after().themeId()).isEqualTo("closeta");
    assertThat(change.after().updatedAt()).isEqualTo(NOW);
    assertThat(change.after().updatedBy()).isEqualTo(5L);
    assertThat(service.config()).isEqualTo(new AppConfigDto("closeta", "2026-10-17T09:00:00.250Z"));
    assertThat(saves).isEqualTo(1);
  }

  @Test
  void 같은_테마로_바꾸면_저장하지_않는다() {
    row = new ThemeSetting("closeta", Instant.EPOCH, 1L);

    ThemeChangeDto change = service.changeTheme(5L, "closeta");

    assertThat(change.changed()).isFalse();
    assertThat(change.after().updatedBy()).isEqualTo(1L);
    assertThat(saves).isZero();
  }

  @Test
  void 허용하지_않는_테마는_400_themeId() {
    assertThatThrownBy(() -> service.changeTheme(5L, "dark"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(e.fieldErrors()).extracting(FieldError::field).containsExactly("themeId");
            });
    assertThat(saves).isZero();
  }
}
