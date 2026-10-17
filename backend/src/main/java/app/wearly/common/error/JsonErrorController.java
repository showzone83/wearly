package app.wearly.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서블릿 컨테이너가 /error로 넘긴 오류(예: 방화벽이 거절한 URL, 필터 밖 예외)도 계약 4장 형식으로 응답한다.
 * 없으면 Spring Boot 기본 형식({ timestamp, status, error, path })이 나간다.
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = raw instanceof Integer value ? HttpStatus.resolve(value) : null;
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = switch (status) {
            case BAD_REQUEST -> ErrorCode.VALIDATION_FAILED;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case CONTENT_TOO_LARGE -> ErrorCode.IMAGE_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        // 상태 코드는 컨테이너가 정한 값을 그대로 쓴다
        ErrorResponse body = new ErrorResponse(status.value(), code.name(), code.defaultMessage(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
