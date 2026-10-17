package app.wearly.common.error;

import org.springframework.http.HttpStatus;

/** 계약 4장의 오류 코드. HTTP 상태와 기본 메시지를 함께 둔다. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않아요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "보안을 위해 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근할 수 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진은 10MB까지 올릴 수 있어요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식이에요."),
    AI_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 분석 횟수를 모두 썼어요."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 분석을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
