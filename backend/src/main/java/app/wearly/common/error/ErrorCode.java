package app.wearly.common.error;

/** 05 문서 4장 + 07 문서의 오류 코드. HTTP 상태 번호와 기본 메시지를 함께 둔다 (유스케이스가 쓰므로 Spring Web 타입은 쓰지 않는다). */
public enum ErrorCode {
    VALIDATION_FAILED(400, "입력값을 확인해 주세요."),
    UNAUTHORIZED(401, "로그인이 필요해요."),
    TOKEN_EXPIRED(401, "로그인이 만료됐어요."),
    TOKEN_INVALID(401, "로그인 정보가 올바르지 않아요."),
    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 맞지 않아요."),
    REFRESH_INVALID(401, "다시 로그인해 주세요."),
    REFRESH_REUSED(401, "보안을 위해 다시 로그인해 주세요."),
    FORBIDDEN(403, "접근할 수 없어요."),
    REAUTH_FAILED(403, "비밀번호가 맞지 않아요."),
    ACCOUNT_SUSPENDED(403, "이용이 정지된 계정이에요."),
    NOT_FOUND(404, "찾을 수 없어요."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청이에요."),
    EMAIL_TAKEN(409, "이미 가입된 이메일이에요."),
    CODE_EXISTS(409, "이미 있는 코드예요."),
    CODE_IN_USE(409, "사용 중인 코드라 삭제할 수 없어요. 대신 비활성화해 주세요."),
    PROVIDER_EXISTS(409, "이미 있는 공급자 ID예요."),
    SECRET_KEY_MISSING(409, "서버에 WEARLY_SECRET_KEY가 없어 웹에서 API 키를 넣을 수 없어요."),
    API_KEY_INVALID(400, "API 키를 확인하지 못했어요."),
    IMAGE_TOO_LARGE(413, "사진은 10MB까지 올릴 수 있어요."),
    UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 형식이에요."),
    AI_LIMIT_EXCEEDED(429, "오늘 AI 분석 횟수를 모두 썼어요."),
    AI_UNAVAILABLE(502, "AI 분석을 지금 할 수 없어요. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(500, "일시적인 오류가 발생했어요.");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
