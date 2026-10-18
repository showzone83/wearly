package app.wearly.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 컨트롤러 밖(Security 필터, /error)에서 오류 응답 JSON을 쓴다. traceId는 TraceIdFilter가 MDC에 넣어 둔 값이다. */
@Component
public class ErrorResponseWriter {

  private static final Logger log = LoggerFactory.getLogger(ErrorResponseWriter.class);

  private final JsonMapper jsonMapper;

  public ErrorResponseWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /** 인증·권한 실패(401·403). 로그는 스택 없이 code·경로·userId */
  public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code)
      throws IOException {
    log.info(
        "인증·권한 오류 code={} {} {} userId={}",
        code,
        request.getMethod(),
        request.getRequestURI(),
        TraceIds.currentUser());
    write(response, code);
  }

  void write(HttpServletResponse response, ErrorCode code) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(code.status());
    response.setCharacterEncoding("UTF-8");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code).body());
  }
}
