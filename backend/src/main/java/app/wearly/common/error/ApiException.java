package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import java.util.List;

/** 계약에 정의된 오류 코드로 응답하는 예외. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldError> fieldErrors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new FieldError(field, message)));
    }

    /** 모은 필드 오류가 있으면 400 VALIDATION_FAILED로 던진다 */
    public static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
        }
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
