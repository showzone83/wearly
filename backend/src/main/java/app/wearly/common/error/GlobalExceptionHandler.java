package app.wearly.common.error;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
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
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * 모든 오류를 오류 응답 { status, code, params?, fieldErrors, traceId } 하나로 바꾼다. 응답에는 문장·예외 메시지·클래스 이름을 넣지
 * 않는다. 자세한 내용은 로그로만 남기고 같은 traceId로 찾는다.
 *
 * <ul>
 *   <li>기본 오류(프로토콜·프레임워크: 입력 형식·401·403·404·405·406·413·415): 카탈로그 코드만. Jackson·Spring 메시지는 WARN
 *       로그에만 (스택 없음)
 *   <li>업무 오류(BusinessException, DomainValidationException, 입력 검증): ErrorCode·필드 코드·params. WARN 로그
 *       (스택 없음, code·경로·userId)
 *   <li>시스템 오류(그 밖의 모든 예외): 500 INTERNAL_ERROR, 일시적 의존성 장애는 503 SERVICE_UNAVAILABLE. ERROR 로그에
 *       스택·요청 정보. 의미가 분명한 DB 예외(낙관적 잠금·고유 제약)는 409 CONFLICT + WARN
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e, HttpServletRequest req) {
    logBusiness(req, e.code(), e);
    return respond(e.code(), e.params(), e.fieldErrors());
  }

  /** 도메인 불변식 위반 (H2: 웹 Bean Validation을 거치지 않은 입력도 도메인이 막는다) */
  @ExceptionHandler(DomainValidationException.class)
  ResponseEntity<Map<String, Object>> handleDomain(
      DomainValidationException e, HttpServletRequest req) {
    logValidation(req, e.errors());
    return validation(e.errors());
  }

  /**
   * @Valid 본문 검증 실패 (MethodArgumentNotValidException 포함)
   */
  @ExceptionHandler(BindException.class)
  ResponseEntity<Map<String, Object>> handleBind(BindException e, HttpServletRequest req) {
    List<FieldError> errors = fieldErrors(e);
    logValidation(req, errors);
    return validation(errors);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<Map<String, Object>> handleMethodValidation(
      HandlerMethodValidationException e, HttpServletRequest req) {
    List<FieldError> errors = new ArrayList<>();
    e.getParameterValidationResults()
        .forEach(
            r -> {
              if (r instanceof Errors pe) {
                errors.addAll(fieldErrors(pe));
                return;
              }
              String name = r.getMethodParameter().getParameterName();
              r.getResolvableErrors()
                  .forEach(
                      err -> {
                        ConstraintViolation<?> v = constraintOf(r, err);
                        errors.add(
                            v == null
                                ? FieldError.invalidFormat(name)
                                : ConstraintCodes.of(name, v));
                      });
            });
    logValidation(req, errors);
    return validation(errors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<Map<String, Object>> handleConstraint(
      ConstraintViolationException e, HttpServletRequest req) {
    List<FieldError> errors =
        e.getConstraintViolations().stream()
            .map(v -> ConstraintCodes.of(leaf(ConstraintCodes.path(v)), v))
            .toList();
    logValidation(req, errors);
    return validation(errors);
  }

  /** JSON 형식 오류, 허용되지 않은 열거형 값, 빈 본문 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    if (e.getCause() instanceof DatabindException de && !de.getPath().isEmpty()) {
      String field = JsonPaths.of(de.getPath());
      boolean enumValue =
          de instanceof InvalidFormatException ife
              && ife.getTargetType() != null
              && ife.getTargetType().isEnum();
      return validation(
          List.of(enumValue ? FieldError.notAllowed(field) : FieldError.invalidFormat(field)));
    }
    // 원인이 없으면 본문이 비어 있다 (Required request body is missing)
    return validation(
        List.of(
            e.getCause() == null
                ? FieldError.required("body")
                : FieldError.invalidFormat("body", "json")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<Map<String, Object>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest req) {
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    boolean enumValue = e.getRequiredType() != null && e.getRequiredType().isEnum();
    return validation(
        List.of(
            enumValue
                ? FieldError.notAllowed(e.getName())
                : FieldError.invalidFormat(e.getName())));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<Map<String, Object>> handleMissingParam(
      MissingServletRequestParameterException e, HttpServletRequest req) {
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    return validation(List.of(FieldError.required(e.getParameterName())));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  ResponseEntity<Map<String, Object>> handleMissingPart(
      MissingServletRequestPartException e, HttpServletRequest req) {
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    return validation(List.of(FieldError.required(e.getRequestPartName())));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<Map<String, Object>> handleMissingHeader(
      MissingRequestHeaderException e, HttpServletRequest req) {
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    return validation(List.of(FieldError.required(e.getHeaderName())));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, Object>> handleTooLarge(
      MaxUploadSizeExceededException e, HttpServletRequest req) {
    logClient(req, ErrorCode.IMAGE_TOO_LARGE, e);
    return respond(ErrorCode.IMAGE_TOO_LARGE);
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<Map<String, Object>> handleMultipart(
      MultipartException e, HttpServletRequest req) {
    if (isSizeExceeded(e)) {
      logClient(req, ErrorCode.IMAGE_TOO_LARGE, e);
      return respond(ErrorCode.IMAGE_TOO_LARGE);
    }
    logClient(req, ErrorCode.VALIDATION_FAILED, e);
    return validation(List.of(FieldError.invalidFormat("image")));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Map<String, Object>> handleMediaType(
      HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
    logClient(req, ErrorCode.UNSUPPORTED_MEDIA_TYPE, e);
    return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Map<String, Object>> handleMethod(
      HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
    logClient(req, ErrorCode.METHOD_NOT_ALLOWED, e);
    return respond(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Map<String, Object>> handleNoResource(
      NoResourceFoundException e, HttpServletRequest req) {
    logClient(req, ErrorCode.NOT_FOUND, e);
    return respond(ErrorCode.NOT_FOUND);
  }

  /** 컨트롤러 안에서 난 인증·권한 예외 (지금은 메서드 보안을 쓰지 않지만 500이 되지 않게) */
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Map<String, Object>> handleAuthentication(
      AuthenticationException e, HttpServletRequest req) {
    logClient(req, ErrorCode.UNAUTHORIZED, e);
    return respond(ErrorCode.UNAUTHORIZED);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> handleAccessDenied(
      AccessDeniedException e, HttpServletRequest req) {
    logClient(req, ErrorCode.FORBIDDEN, e);
    return respond(ErrorCode.FORBIDDEN);
  }

  /**
   * 그 밖의 모든 예외. 위에서 따로 받지 않은 Spring MVC 예외(406, 경로 변수 누락, 비동기 시간 초과 등)는 HTTP 상태로 카탈로그 코드를 고르고, 나머지는
   * 시스템 오류로 분류한다
   */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> handleUnknown(Exception e, HttpServletRequest req) {
    if (e instanceof org.springframework.web.ErrorResponse er) {
      ErrorCode code = ErrorCodes.forStatus(er.getStatusCode().value());
      if (code.status() >= 500) {
        logSystem(req, code, e);
      } else {
        logClient(req, code, e);
      }
      return respond(code);
    }
    ErrorCode code = SystemErrors.classify(e);
    if (SystemErrors.expected(code)) {
      logExpectedSystem(req, code, e);
    } else {
      logSystem(req, code, e);
    }
    return respond(code);
  }

  private static List<FieldError> fieldErrors(Errors e) {
    List<FieldError> errors = new ArrayList<>();
    e.getFieldErrors()
        .forEach(
            fe -> {
              ConstraintViolation<?> v =
                  fe.contains(ConstraintViolation.class)
                      ? fe.unwrap(ConstraintViolation.class)
                      : null;
              errors.add(
                  v == null
                      ? FieldError.invalidFormat(fe.getField())
                      : ConstraintCodes.of(fe.getField(), v));
            });
    // 객체 수준 오류: 객체 이름(자바 클래스 이름)을 내보내지 않고 body로
    e.getGlobalErrors().forEach(ge -> errors.add(FieldError.invalidFormat("body")));
    return errors;
  }

  private static ConstraintViolation<?> constraintOf(
      org.springframework.validation.method.ParameterValidationResult r,
      org.springframework.context.MessageSourceResolvable err) {
    try {
      return r.unwrap(err, ConstraintViolation.class);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** 메서드 검증 경로(method.arg0.field)에서 마지막 이름만 */
  private static String leaf(String path) {
    int dot = path.lastIndexOf('.');
    return dot < 0 ? path : path.substring(dot + 1);
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

  private static ResponseEntity<Map<String, Object>> validation(List<FieldError> errors) {
    return respond(ErrorCode.VALIDATION_FAILED, Map.of(), errors);
  }

  private static ResponseEntity<Map<String, Object>> respond(ErrorCode code) {
    return respond(code, Map.of(), List.of());
  }

  private static ResponseEntity<Map<String, Object>> respond(
      ErrorCode code, Map<String, Object> params, List<FieldError> errors) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, params, errors).body());
  }

  /** 업무 오류: 스택 없이 code·경로·userId (예외 메시지는 code와 필드 코드뿐) */
  private static void logBusiness(HttpServletRequest req, ErrorCode code, Exception e) {
    log.warn(
        "업무 오류 code={} {} {} userId={} detail={}",
        code,
        req.getMethod(),
        req.getRequestURI(),
        TraceIds.currentUser(),
        e.getMessage());
  }

  /**
   * 입력 검증 실패: 필드 이름과 필드 코드만 남긴다. Spring·Bean Validation 메시지 원문에는 거부된 값(비밀번호 등)이 그대로 들어 있어 로그에도 남기지
   * 않는다.
   */
  private static void logValidation(HttpServletRequest req, List<FieldError> errors) {
    StringBuilder fields = new StringBuilder();
    for (FieldError f : errors) {
      if (!fields.isEmpty()) {
        fields.append(',');
      }
      fields.append(f.field()).append(':').append(f.code());
    }
    log.warn(
        "입력 오류 code={} {} {} userId={} fields=[{}]",
        ErrorCode.VALIDATION_FAILED,
        req.getMethod(),
        req.getRequestURI(),
        TraceIds.currentUser(),
        fields);
  }

  /** 기본 오류: 예외 종류만 남긴다. Jackson·Spring 메시지 원문에는 요청에 들어 있던 값(비밀번호 등)이 섞일 수 있어 로그에도 남기지 않는다. */
  private static void logClient(HttpServletRequest req, ErrorCode code, Exception e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    log.warn(
        "요청 오류 code={} {} {} userId={} cause={} root={}",
        code,
        req.getMethod(),
        req.getRequestURI(),
        TraceIds.currentUser(),
        e.getClass().getName(),
        root.getClass().getName());
  }

  private static void logExpectedSystem(HttpServletRequest req, ErrorCode code, Exception e) {
    log.warn(
        "DB 충돌 code={} {} {} userId={} cause={} sqlState={}",
        code,
        req.getMethod(),
        req.getRequestURI(),
        TraceIds.currentUser(),
        e.getClass().getName(),
        SystemErrors.sqlState(e));
  }

  /** 시스템 오류: 스택·요청 정보 */
  private static void logSystem(HttpServletRequest req, ErrorCode code, Exception e) {
    log.error(
        "시스템 오류 code={} {} {} query={} userId={}",
        code,
        req.getMethod(),
        req.getRequestURI(),
        req.getQueryString(),
        TraceIds.currentUser(),
        e);
  }
}
