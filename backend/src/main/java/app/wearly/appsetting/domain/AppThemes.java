package app.wearly.appsetting.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.List;

/**
 * 전체 사용자의 디자인 테마 (12 문서 2·3장). 테마의 색·마스코트는 앱 코드에 있고, 서버는 어떤 테마를 쓸지만 저장한다. 테마를 늘리면 여기와 앱 themes.ts를
 * 함께 고친다.
 */
public final class AppThemes {

  public static final String NYANG = "nyang";
  public static final String CLOSETA = "closeta";

  /** 기본 테마 (설정 행이 없을 때) */
  public static final String DEFAULT = NYANG;

  public static final List<String> ALLOWED = List.of(NYANG, CLOSETA);

  private AppThemes() {}

  /** 필드 이름은 themeId. 허용 값이 아니면 400 VALIDATION_FAILED로 돌려준다 */
  public static List<FieldError> validate(String themeId) {
    List<FieldError> errors = new ArrayList<>();
    if (themeId == null || themeId.isBlank()) {
      errors.add(FieldError.required("themeId"));
    } else if (!ALLOWED.contains(themeId)) {
      errors.add(FieldError.notAllowed("themeId", ALLOWED));
    }
    return errors;
  }
}
