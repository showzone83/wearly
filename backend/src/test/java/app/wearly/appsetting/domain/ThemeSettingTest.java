package app.wearly.appsetting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 테마 값 규칙과 version (12 문서 3장, Spring 없이) */
class ThemeSettingTest {

  @Test
  void 허용_테마는_nyang과_closeta뿐이다() {
    assertThat(AppThemes.validate("nyang")).isEmpty();
    assertThat(AppThemes.validate("closeta")).isEmpty();
    assertThat(AppThemes.validate("NYANG"))
        .containsExactly(FieldError.notAllowed("themeId", List.of("nyang", "closeta")));
    assertThat(AppThemes.validate(" ")).containsExactly(FieldError.required("themeId"));
    assertThat(AppThemes.validate(null)).extracting(FieldError::field).containsExactly("themeId");
  }

  @Test
  void 처음_상태는_nyang이고_version은_initial이다() {
    ThemeSetting s = ThemeSetting.defaults();
    assertThat(s.themeId()).isEqualTo("nyang");
    assertThat(s.updatedAt()).isNull();
    assertThat(s.updatedBy()).isNull();
    assertThat(s.version()).isEqualTo(ThemeSetting.INITIAL_VERSION);
  }

  @Test
  void 바꾸면_시각과_관리자가_남고_version은_밀리초_ISO_시각이다() {
    ThemeSetting changed =
        ThemeSetting.defaults()
            .changeTo("closeta", 3L, Instant.parse("2026-10-17T05:06:07.123456Z"));
    assertThat(changed)
        .isEqualTo(new ThemeSetting("closeta", Instant.parse("2026-10-17T05:06:07.123Z"), 3L));
    assertThat(changed.version()).isEqualTo("2026-10-17T05:06:07.123Z");
    assertThatThrownBy(() -> changed.changeTo("dark", 3L, Instant.EPOCH))
        .isInstanceOf(DomainValidationException.class);
  }
}
