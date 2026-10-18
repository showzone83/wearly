package app.wearly.common.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 필드 하나의 검증 오류. 오류 응답의 fieldErrors 항목 { field, code, params? }이 된다. 문장은 넣지 않는다: 화면 문구는 앱·관리자가
 * code별로 정한다. params에는 기준 값(최대 길이·허용 목록 등)만 넣고, 이름은 code가 정한 것만 쓴다.
 */
public record FieldError(String field, FieldErrorCode code, Map<String, Object> params) {

  public FieldError {
    if (field == null || code == null) {
      throw new IllegalArgumentException("field와 code는 필수다");
    }
    Map<String, Object> copy = new LinkedHashMap<>();
    if (params != null) {
      params.forEach(
          (k, v) -> {
            if (!code.params().contains(k)) {
              throw new IllegalArgumentException(code + "에 없는 params 이름: " + k);
            }
            if (v != null) {
              copy.put(k, v instanceof Collection<?> c ? List.copyOf(c) : v);
            }
          });
    }
    params = Collections.unmodifiableMap(copy);
  }

  public FieldError(String field, FieldErrorCode code) {
    this(field, code, Map.of());
  }

  public static FieldError required(String field) {
    return new FieldError(field, FieldErrorCode.REQUIRED);
  }

  public static FieldError tooLong(String field, int max) {
    return new FieldError(field, FieldErrorCode.TOO_LONG, Map.of("max", max));
  }

  public static FieldError tooShort(String field, int min) {
    return new FieldError(field, FieldErrorCode.TOO_SHORT, Map.of("min", min));
  }

  public static FieldError tooMany(String field, int max) {
    return new FieldError(field, FieldErrorCode.TOO_MANY, Map.of("max", max));
  }

  /** 숫자 범위 밖. min·max 중 null은 빠진다 */
  public static FieldError outOfRange(String field, Number min, Number max) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("min", min);
    p.put("max", max);
    return new FieldError(field, FieldErrorCode.OUT_OF_RANGE, p);
  }

  /** 허용 목록·코드에 없는 값 (목록을 알려 주지 않을 때) */
  public static FieldError notAllowed(String field) {
    return new FieldError(field, FieldErrorCode.NOT_ALLOWED);
  }

  /** 고정 허용 목록에 없는 값 */
  public static FieldError notAllowed(String field, Collection<?> allowed) {
    return new FieldError(
        field, FieldErrorCode.NOT_ALLOWED, Map.of("allowed", List.copyOf(allowed)));
  }

  public static FieldError invalidFormat(String field) {
    return new FieldError(field, FieldErrorCode.INVALID_FORMAT);
  }

  /** format: 기대 형식 이름 (예: YYYY-MM-DD, #RRGGBB) */
  public static FieldError invalidFormat(String field, String format) {
    return new FieldError(field, FieldErrorCode.INVALID_FORMAT, Map.of("format", format));
  }

  /** expected: boolean·string·integer·number·object·array */
  public static FieldError invalidType(String field, String expected) {
    return new FieldError(field, FieldErrorCode.INVALID_TYPE, Map.of("expected", expected));
  }
}
