package app.wearly.sharedkernel.code;

import app.wearly.common.domain.FieldError;
import java.util.List;
import java.util.Objects;

/**
 * 코드 값 검증 (06 문서 4장 "검증 규칙"). - 그룹에 없는 코드는 거부한다. - 새로 고르는 값은 고를 수 있는(활성) 코드만 허용한다. 이전 값과
 * 같으면(PATCH에서 안 바꾼 필드) 비활성이어도 둔다.
 */
public final class CodeChecks {

  public static final String UNKNOWN = "허용되지 않는 값이에요.";
  public static final String INACTIVE = "지금은 고를 수 없는 값이에요.";

  private CodeChecks() {}

  /** null은 검사하지 않는다 (필수 여부는 Bean Validation이 본다) */
  public static void one(
      CodeSnapshot codes,
      String group,
      String field,
      String value,
      String previous,
      List<FieldError> errors) {
    if (value == null) {
      return;
    }
    if (!codes.known(group, value)) {
      errors.add(new FieldError(field, UNKNOWN));
    } else if (!Objects.equals(value, previous) && !codes.selectable(group, value)) {
      errors.add(new FieldError(field, INACTIVE));
    }
  }

  /** 배열: 이전 배열에 있던 값은 비활성이어도 둔다. 필드 이름은 seasons[1] 형태 */
  public static void many(
      CodeSnapshot codes,
      String group,
      String field,
      List<String> values,
      List<String> previous,
      List<FieldError> errors) {
    if (values == null) {
      return;
    }
    for (int i = 0; i < values.size(); i++) {
      String v = values.get(i);
      if (v == null) {
        continue;
      }
      String name = field + "[" + i + "]";
      if (!codes.known(group, v)) {
        errors.add(new FieldError(name, UNKNOWN));
      } else if ((previous == null || !previous.contains(v)) && !codes.selectable(group, v)) {
        errors.add(new FieldError(name, INACTIVE));
      }
    }
  }
}
