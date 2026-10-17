package app.wearly.common.error;

import java.util.List;

/** 계약에 정의된 오류 코드로 응답하는 예외. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<ErrorResponse.FieldError> fieldErrors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new ErrorResponse.FieldError(field, message)));
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
