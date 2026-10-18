package app.wearly.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 서블릿 오류 경로(/error). 컨트롤러에 닿기 전(필터)에서 난 예외나 서블릿 컨테이너가 정한 오류 상태도 같은 오류 응답 형식으로 쓴다. Spring Boot의 기본
 * BasicErrorController(예외 메시지·경로·whitelabel 화면)를 대신한다. API가 아니라 오류 처리기라 웹 어댑터가 아닌 여기에 둔다.
 */
@Controller
@RequestMapping("${server.error.path:/error}")
public class ErrorPageController implements ErrorController {

  private static final Logger log = LoggerFactory.getLogger(ErrorPageController.class);

  @RequestMapping
  ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
    ErrorCode code = classify(request);
    return ResponseEntity.status(code.status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of(code).body());
  }

  private static ErrorCode classify(HttpServletRequest request) {
    Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    Object method = request.getMethod();
    if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable e) {
      ErrorCode code = SystemErrors.classify(e);
      if (SystemErrors.expected(code)) {
        log.warn(
            "DB 충돌(필터) code={} {} {} cause={} sqlState={}",
            code,
            method,
            path,
            e.getClass().getName(),
            SystemErrors.sqlState(e));
      } else {
        log.error("시스템 오류(필터) code={} {} {}", code, method, path, e);
      }
      return code;
    }
    int status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer s ? s : 500;
    ErrorCode code = ErrorCodes.forStatus(status);
    if (status >= 500) {
      log.error("서버 오류 상태 code={} status={} {} {}", code, status, method, path);
    } else {
      log.warn("요청 오류 상태 code={} status={} {} {}", code, status, method, path);
    }
    return code;
  }
}
