package app.wearly.common.error;

/** HTTP 상태 번호 → 카탈로그 코드 (Spring·서블릿이 정한 상태를 우리 형식으로 바꿀 때) */
final class ErrorCodes {

  private ErrorCodes() {}

  static ErrorCode forStatus(int status) {
    return switch (status) {
      case 400 -> ErrorCode.VALIDATION_FAILED;
      case 401 -> ErrorCode.UNAUTHORIZED;
      case 403 -> ErrorCode.FORBIDDEN;
      case 404 -> ErrorCode.NOT_FOUND;
      case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
      case 406 -> ErrorCode.NOT_ACCEPTABLE;
      case 409 -> ErrorCode.CONFLICT;
      case 413 -> ErrorCode.IMAGE_TOO_LARGE;
      case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
      case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
      default ->
          status >= 400 && status < 500 ? ErrorCode.VALIDATION_FAILED : ErrorCode.INTERNAL_ERROR;
    };
  }
}
