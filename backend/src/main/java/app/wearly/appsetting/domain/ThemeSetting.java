package app.wearly.appsetting.domain;

import app.wearly.common.domain.DomainValidationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 테마 설정 (tb_app_setting의 theme 행). 한 번도 바꾸지 않았으면 updatedAt·updatedBy가 null이다.
 *
 * @param themeId 테마 ID (nyang · closeta)
 * @param updatedAt 관리자가 마지막으로 바꾼 시각 (밀리초까지)
 * @param updatedBy 바꾼 관리자 회원 ID (탈퇴하면 null)
 */
public record ThemeSetting(String themeId, Instant updatedAt, Long updatedBy) {

  /** 처음 상태(바꾼 적 없음)의 version */
  public static final String INITIAL_VERSION = "initial";

  /** 설정 행이 없을 때 */
  public static ThemeSetting defaults() {
    return new ThemeSetting(AppThemes.DEFAULT, null, null);
  }

  /** 관리자가 바꾼 결과. 허용 값이 아니면 DomainValidationException (유스케이스가 먼저 AppThemes.validate로 본다) */
  public ThemeSetting changeTo(String newThemeId, long adminId, Instant now) {
    DomainValidationException.throwIfAny(AppThemes.validate(newThemeId));
    return new ThemeSetting(newThemeId, now.truncatedTo(ChronoUnit.MILLIS), adminId);
  }

  /**
   * GET /app/config의 version과 ETag. 바뀐 시각(ISO-8601 UTC, 밀리초)이고, 바꾼 적이 없으면 "initial". 관리자가 바꾸면 반드시
   * 달라진다.
   */
  public String version() {
    return updatedAt == null
        ? INITIAL_VERSION
        : updatedAt.truncatedTo(ChronoUnit.MILLIS).toString();
  }
}
