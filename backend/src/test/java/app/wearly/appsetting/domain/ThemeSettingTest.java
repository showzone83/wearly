package app.wearly.appsetting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 테마·계절 모드 값 규칙과 version (12 문서 3장, 이슈 #6, Spring 없이) */
class ThemeSettingTest {

  static final List<String> MODES = List.of("OFF", "AUTO", "SPRING", "SUMMER", "FALL", "WINTER");

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
  void 계절_모드는_6개이고_대문자만_허용한다() {
    assertThat(SeasonMode.ALLOWED).isEqualTo(MODES);
    for (String m : MODES) {
      assertThat(SeasonMode.validate(m)).isEmpty();
    }
    assertThat(SeasonMode.validate("auto"))
        .containsExactly(FieldError.notAllowed("seasonMode", MODES));
    assertThat(SeasonMode.validate("AUTUMN"))
        .containsExactly(FieldError.notAllowed("seasonMode", MODES));
    assertThat(SeasonMode.validate("")).containsExactly(FieldError.required("seasonMode"));
  }

  @Test
  void 저장값이_없거나_모르는_계절_모드면_OFF로_읽는다() {
    assertThat(SeasonMode.parseOrDefault(null)).isEqualTo(SeasonMode.OFF);
    assertThat(SeasonMode.parseOrDefault("HOLIDAY")).isEqualTo(SeasonMode.OFF);
    assertThat(SeasonMode.parseOrDefault(3)).isEqualTo(SeasonMode.OFF);
    assertThat(SeasonMode.parseOrDefault("FALL")).isEqualTo(SeasonMode.FALL);
    assertThat(new ThemeSetting("nyang", null, null, null).seasonMode()).isEqualTo(SeasonMode.OFF);
  }

  @Test
  void 처음_상태는_nyang_OFF이고_version은_initial이다() {
    ThemeSetting s = ThemeSetting.defaults();
    assertThat(s.themeId()).isEqualTo("nyang");
    assertThat(s.seasonMode()).isEqualTo(SeasonMode.OFF);
    assertThat(s.updatedAt()).isNull();
    assertThat(s.updatedBy()).isNull();
    assertThat(s.version()).isEqualTo(ThemeSetting.INITIAL_VERSION);
  }

  @Test
  void 바꾸면_시각과_관리자가_남고_version은_밀리초_ISO_시각이다() {
    ThemeSetting changed =
        ThemeSetting.defaults()
            .changeTo("closeta", null, 3L, Instant.parse("2026-10-17T05:06:07.123456Z"));
    assertThat(changed)
        .isEqualTo(
            new ThemeSetting(
                "closeta", SeasonMode.OFF, Instant.parse("2026-10-17T05:06:07.123Z"), 3L));
    assertThat(changed.version()).isEqualTo("2026-10-17T05:06:07.123Z");
    assertThatThrownBy(() -> changed.changeTo("dark", null, 3L, Instant.EPOCH))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void 보낸_값만_바꾸고_나머지는_그대로_둔다() {
    ThemeSetting base = new ThemeSetting("closeta", SeasonMode.WINTER, Instant.EPOCH, 1L);
    Instant now = Instant.parse("2026-12-01T00:00:00Z");

    ThemeSetting onlySeason = base.changeTo(null, "AUTO", 2L, now);
    assertThat(onlySeason).isEqualTo(new ThemeSetting("closeta", SeasonMode.AUTO, now, 2L));
    assertThat(onlySeason.version()).isNotEqualTo(base.version());

    ThemeSetting both = base.changeTo("nyang", "SPRING", 2L, now);
    assertThat(both).isEqualTo(new ThemeSetting("nyang", SeasonMode.SPRING, now, 2L));

    assertThat(base.sameValues(base.changeTo("closeta", "WINTER", 2L, now))).isTrue();
    assertThat(base.sameValues(onlySeason)).isFalse();
  }

  @Test
  void 변경_요청은_둘_다_없으면_themeId_필수이고_필드마다_따로_본다() {
    assertThat(ThemeSetting.validateChange(null, null))
        .containsExactly(FieldError.required("themeId"));
    assertThat(ThemeSetting.validateChange("nyang", null)).isEmpty();
    assertThat(ThemeSetting.validateChange(null, "SUMMER")).isEmpty();
    assertThat(ThemeSetting.validateChange("dark", "summer"))
        .extracting(FieldError::field)
        .containsExactly("themeId", "seasonMode");
    assertThat(ThemeSetting.validateChange("", null))
        .containsExactly(FieldError.required("themeId"));
  }
}
