package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bean Validation 위반 → 필드 오류 코드. 애너테이션의 message 문장은 쓰지 않고 애너테이션 종류와 속성(min·max)으로 코드·params를 정한다.
 * 애너테이션 클래스는 이름으로만 본다 (검증 애너테이션은 웹 어댑터에만 둔다, ArchUnit).
 */
public final class ConstraintCodes {

  private static final String JAKARTA = "jakarta.validation.constraints.";
  private static final String HIBERNATE = "org.hibernate.validator.constraints.";

  private ConstraintCodes() {}

  /** 위반 경로를 JSON 필드 이름으로 (seasons[1].<list element> → seasons[1]). 컨테이너 요소 같은 내부 노드 이름은 뺀다 */
  public static String path(ConstraintViolation<?> violation) {
    StringBuilder sb = new StringBuilder();
    for (Path.Node node : violation.getPropertyPath()) {
      if (node.isInIterable()) {
        if (node.getIndex() != null) {
          sb.append('[').append(node.getIndex()).append(']');
        } else if (node.getKey() != null) {
          sb.append('[').append(node.getKey()).append(']');
        }
      }
      String name = node.getName();
      if (name != null && !name.isEmpty() && !name.startsWith("<")) {
        if (!sb.isEmpty()) {
          sb.append('.');
        }
        sb.append(name);
      }
    }
    return sb.isEmpty() ? "body" : sb.toString();
  }

  public static FieldError of(String field, ConstraintViolation<?> violation) {
    String type = violation.getConstraintDescriptor().getAnnotation().annotationType().getName();
    Map<String, Object> attrs = violation.getConstraintDescriptor().getAttributes();
    Object value = violation.getInvalidValue();
    return switch (type) {
      case JAKARTA + "NotNull", JAKARTA + "NotBlank", JAKARTA + "NotEmpty" ->
          FieldError.required(field);
      case JAKARTA + "Size", HIBERNATE + "Length" -> size(field, value, attrs);
      case JAKARTA + "Min", JAKARTA + "DecimalMin", JAKARTA + "PositiveOrZero" ->
          FieldError.outOfRange(field, bound(attrs.get("value"), 0), null);
      case JAKARTA + "Positive" -> FieldError.outOfRange(field, 1, null);
      case JAKARTA + "Max", JAKARTA + "DecimalMax" ->
          FieldError.outOfRange(field, null, bound(attrs.get("value"), 0));
      case HIBERNATE + "Range" ->
          FieldError.outOfRange(field, bound(attrs.get("min"), 0), bound(attrs.get("max"), 0));
      default -> FieldError.invalidFormat(field);
    };
  }

  private static FieldError size(String field, Object value, Map<String, Object> attrs) {
    int min = ((Number) attrs.getOrDefault("min", 0)).intValue();
    int max = ((Number) attrs.getOrDefault("max", Integer.MAX_VALUE)).intValue();
    boolean many = value instanceof Collection<?> || value instanceof Map<?, ?> || isArray(value);
    int length = length(value);
    if (length < min) {
      return min <= 1 ? FieldError.required(field) : FieldError.tooShort(field, min);
    }
    if (many) {
      return FieldError.tooMany(field, max);
    }
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("max", max);
    return new FieldError(field, FieldErrorCode.TOO_LONG, p);
  }

  private static int length(Object value) {
    if (value instanceof CharSequence s) {
      return s.length();
    }
    if (value instanceof Collection<?> c) {
      return c.size();
    }
    if (value instanceof Map<?, ?> m) {
      return m.size();
    }
    return isArray(value) ? Array.getLength(value) : 0;
  }

  private static boolean isArray(Object value) {
    return value != null && value.getClass().isArray();
  }

  /** long·문자열(DecimalMin) 속성을 숫자로. 정수면 정수로 둔다 */
  private static Number bound(Object raw, long fallback) {
    if (raw instanceof Number n) {
      long l = n.longValue();
      return l == (int) l ? Integer.valueOf((int) l) : Long.valueOf(l);
    }
    if (raw instanceof String s) {
      try {
        return new java.math.BigDecimal(s).stripTrailingZeros();
      } catch (NumberFormatException e) {
        return fallback;
      }
    }
    return fallback;
  }
}
