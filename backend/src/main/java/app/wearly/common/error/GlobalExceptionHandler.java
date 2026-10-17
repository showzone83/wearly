package app.wearly.common.error;

import app.wearly.common.domain.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.DatabindException;

/** 모든 오류를 계약 4장 형식으로 바꾼다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.code(), e.getMessage(), e.fieldErrors());
    }

    /** @Valid 본문 검증 실패 (MethodArgumentNotValidException 포함) */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<FieldError> errors = new ArrayList<>();
        e.getFieldErrors().forEach(fe -> errors.add(new FieldError(fe.getField(), fe.getDefaultMessage())));
        e.getGlobalErrors().forEach(ge -> errors.add(new FieldError(ge.getObjectName(), ge.getDefaultMessage())));
        return validation(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldError> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                errors.add(new FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage()))));
        return validation(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        List<FieldError> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(errors);
    }

    /** JSON 형식 오류, 허용되지 않은 열거형 값 등 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof DatabindException de && !de.getPath().isEmpty()) {
            return validation(List.of(new FieldError(JsonPaths.of(de.getPath()), "허용되지 않는 값이에요.")));
        }
        return validation(List.of(new FieldError("body", "요청 본문을 읽을 수 없어요.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(List.of(new FieldError(e.getName(), "허용되지 않는 값이에요.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return validation(List.of(new FieldError(e.getParameterName(), "필수 값이에요.")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return validation(List.of(new FieldError(e.getRequestPartName(), "필수 값이에요.")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return respond(ErrorCode.IMAGE_TOO_LARGE, ErrorCode.IMAGE_TOO_LARGE.defaultMessage(), List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
        if (isSizeExceeded(e)) {
            return respond(ErrorCode.IMAGE_TOO_LARGE, ErrorCode.IMAGE_TOO_LARGE.defaultMessage(), List.of());
        }
        return validation(List.of(new FieldError("image", "사진을 읽을 수 없어요.")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("처리하지 못한 오류", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of());
    }

    private static boolean isSizeExceeded(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.contains("SizeLimitExceeded") || name.contains("SizeException")) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> validation(List<FieldError> errors) {
        return respond(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, List<FieldError> errors) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message, errors));
    }
}
