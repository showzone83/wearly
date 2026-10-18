package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업무 규칙 위반 (중복 이메일, 옷이 모자람, 사용 중지 옷 고정, AI 한도, 남의 리소스 등). 응답은 ErrorCode의 code·status와 params,
 * fieldErrors뿐이다.
 *
 * <p>문장(String)을 받는 생성자는 두지 않는다: 예외 메시지·외부 응답 같은 동적 문자열이 응답으로 나가지 않게 한다 (ArchUnit이 확인). 자세한 사정은
 * 서비스가 로그로 남긴다. params 이름은 ErrorCode가 정한 것만 쓴다.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode code;
  private final transient Map<String, Object> params;
  private final transient List<FieldError> fieldErrors;

  public BusinessException(ErrorCode code) {
    this(code, Map.of(), List.of());
  }

  public BusinessException(ErrorCode code, Map<String, Object> params) {
    this(code, params, List.of());
  }

  public BusinessException(ErrorCode code, List<FieldError> fieldErrors) {
    this(code, Map.of(), fieldErrors);
  }

  private BusinessException(
      ErrorCode code, Map<String, Object> params, List<FieldError> fieldErrors) {
    // 로그용 메시지: 코드와 필드 오류 코드만 (응답에는 쓰지 않는다)
    super(code.name() + (fieldErrors.isEmpty() ? "" : " " + summary(fieldErrors)));
    this.code = code;
    this.params = checked(code, params);
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public static BusinessException notFound() {
    return new BusinessException(ErrorCode.NOT_FOUND);
  }

  public static BusinessException validation(FieldError error) {
    return new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(error));
  }

  public static BusinessException validation(String field, FieldErrorCode code) {
    return validation(new FieldError(field, code));
  }

  /** 모은 필드 오류가 있으면 400 VALIDATION_FAILED로 던진다 */
  public static void throwIfAny(List<FieldError> errors) {
    if (!errors.isEmpty()) {
      throw new BusinessException(ErrorCode.VALIDATION_FAILED, errors);
    }
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, Object> params() {
    return params;
  }

  public List<FieldError> fieldErrors() {
    return fieldErrors;
  }

  private static Map<String, Object> checked(ErrorCode code, Map<String, Object> params) {
    Map<String, Object> copy = new LinkedHashMap<>();
    params.forEach(
        (k, v) -> {
          if (!code.params().contains(k)) {
            throw new IllegalArgumentException(code + "에 없는 params 이름: " + k);
          }
          if (v != null) {
            copy.put(k, v instanceof Collection<?> c ? List.copyOf(c) : v);
          }
        });
    return Collections.unmodifiableMap(copy);
  }

  private static String summary(List<FieldError> errors) {
    StringBuilder sb = new StringBuilder("[");
    for (FieldError e : errors) {
      if (sb.length() > 1) {
        sb.append(", ");
      }
      sb.append(e.field()).append('=').append(e.code());
    }
    return sb.append(']').toString();
  }
}
