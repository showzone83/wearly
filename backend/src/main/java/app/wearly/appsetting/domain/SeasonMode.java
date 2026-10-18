package app.wearly.appsetting.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 계절 테마 모드 (이슈 #6). 테마(냥깨비·Closeta) 위에 계절 색을 덮어쓸지 정한다. 계절 색 값과 AUTO의 계절 계산(한국 날짜 기준 3~5월 봄, 6~8월
 * 여름, 9~11월 가을, 12~2월 겨울)은 앱 코드(mobile/src/data/seasons.ts)에 있고, 서버는 모드만 저장한다.
 */
public enum SeasonMode {
  /** 계절 색을 쓰지 않는다 (기본, 지금 화면 그대로) */
  OFF,
  /** 앱이 날짜(Asia/Seoul)로 계절을 골라 쓴다 */
  AUTO,
  /** 시즌 이벤트용 고정 계절 */
  SPRING,
  SUMMER,
  FALL,
  WINTER;

  /** 설정 행에 값이 없을 때 */
  public static final SeasonMode DEFAULT = OFF;

  public static final List<String> ALLOWED = Arrays.stream(values()).map(SeasonMode::name).toList();

  /** 필드 이름은 seasonMode. 대소문자까지 같아야 한다 */
  public static List<FieldError> validate(String value) {
    List<FieldError> errors = new ArrayList<>();
    if (value == null || value.isBlank()) {
      errors.add(FieldError.required("seasonMode"));
    } else if (!ALLOWED.contains(value)) {
      errors.add(FieldError.notAllowed("seasonMode", ALLOWED));
    }
    return errors;
  }

  /** 저장된 값 읽기. 없거나 모르는 값이면 기본(OFF) */
  public static SeasonMode parseOrDefault(Object stored) {
    return stored instanceof String s && ALLOWED.contains(s) ? valueOf(s) : DEFAULT;
  }
}
