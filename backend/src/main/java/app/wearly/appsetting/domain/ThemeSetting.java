package app.wearly.appsetting.domain;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 테마 설정 (tb_app_setting의 theme 행). 한 번도 바꾸지 않았으면 updatedAt·updatedBy가 null이다.
 *
 * @param themeId 테마 ID (nyang · closeta)
 * @param seasonMode 계절 테마 모드 (이슈 #6, 저장값이 없으면 OFF)
 * @param updatedAt 관리자가 마지막으로 바꾼 시각 (밀리초까지)
 * @param updatedBy 바꾼 관리자 회원 ID (탈퇴하면 null)
 */
public record ThemeSetting(
    String themeId, SeasonMode seasonMode, Instant updatedAt, Long updatedBy) {

  /** 처음 상태(바꾼 적 없음)의 version */
  public static final String INITIAL_VERSION = "initial";

  public ThemeSetting {
    if (seasonMode == null) {
      seasonMode = SeasonMode.DEFAULT;
    }
  }

  /** 설정 행이 없을 때 */
  public static ThemeSetting defaults() {
    return new ThemeSetting(AppThemes.DEFAULT, SeasonMode.DEFAULT, null, null);
  }

  /** 관리자 변경 요청 검증. 보낸 값만 바꾸므로 둘 다 null이면 themeId 필수 오류 하나, 보낸 값은 각각 허용 값인지 본다(빈 문자열은 필수 오류). */
  public static List<FieldError> validateChange(String themeId, String seasonMode) {
    List<FieldError> errors = new ArrayList<>();
    if (themeId == null && seasonMode == null) {
      errors.add(FieldError.required("themeId"));
      return errors;
    }
    if (themeId != null) {
      errors.addAll(AppThemes.validate(themeId));
    }
    if (seasonMode != null) {
      errors.addAll(SeasonMode.validate(seasonMode));
    }
    return errors;
  }

  /**
   * 관리자가 바꾼 결과. null인 값은 지금 값을 그대로 둔다. 허용 값이 아니면 DomainValidationException (유스케이스가 먼저
   * validateChange로 본다)
   */
  public ThemeSetting changeTo(String newThemeId, String newSeasonMode, long adminId, Instant now) {
    DomainValidationException.throwIfAny(validateChange(newThemeId, newSeasonMode));
    return new ThemeSetting(
        newThemeId == null ? themeId : newThemeId,
        newSeasonMode == null ? seasonMode : SeasonMode.valueOf(newSeasonMode),
        now.truncatedTo(ChronoUnit.MILLIS),
        adminId);
  }

  /** 테마 ID와 계절 모드가 같은지 (바꾼 시각·관리자는 보지 않는다) */
  public boolean sameValues(ThemeSetting other) {
    return themeId.equals(other.themeId) && seasonMode == other.seasonMode;
  }

  /**
   * GET /app/config의 version과 ETag. 바뀐 시각(ISO-8601 UTC, 밀리초)이고, 바꾼 적이 없으면 "initial". 관리자가 테마나 계절
   * 모드를 바꾸면 반드시 달라진다. 응답은 저장값만으로 정해지므로(서버가 날짜로 계절을 계산하지 않음) 날짜가 바뀌어도 ETag는 그대로 맞다.
   */
  public String version() {
    return updatedAt == null
        ? INITIAL_VERSION
        : updatedAt.truncatedTo(ChronoUnit.MILLIS).toString();
  }
}
