package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import java.util.List;

/** 계약 4장 오류 응답 본문. */
public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors) {

  public static ErrorResponse of(ErrorCode code) {
    return of(code, code.defaultMessage(), List.of());
  }

  public static ErrorResponse of(ErrorCode code, String message, List<FieldError> fieldErrors) {
    return new ErrorResponse(code.status(), code.name(), message, fieldErrors);
  }
}
