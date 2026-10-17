package app.wearly.common.domain;

import java.util.List;

/**
 * 도메인 불변식을 어긴 입력 (필드 오류 목록). 웹 어댑터의 Bean Validation은 빠른 실패용이고, 다른 입력 어댑터나 다른 컨텍스트가 유스케이스를 불러도 도메인이
 * 이 예외로 최종 보증한다. 전역 예외 처리가 400 VALIDATION_FAILED로 바꾼다.
 */
public class DomainValidationException extends RuntimeException {

  private final transient List<FieldError> errors;

  public DomainValidationException(List<FieldError> errors) {
    super("입력값이 도메인 규칙에 맞지 않아요: " + errors);
    this.errors = List.copyOf(errors);
  }

  public static void throwIfAny(List<FieldError> errors) {
    if (!errors.isEmpty()) {
      throw new DomainValidationException(errors);
    }
  }

  public List<FieldError> errors() {
    return errors;
  }
}
