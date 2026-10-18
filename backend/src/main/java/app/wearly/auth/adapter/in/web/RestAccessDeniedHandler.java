package app.wearly.auth.adapter.in.web;

import app.wearly.common.error.ErrorCode;
import app.wearly.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 권한 부족(403)을 오류 형식으로 쓴다. 2단계 인증을 거치지 않은 관리자 세션이면 MFA_REQUIRED, 그 밖은 FORBIDDEN (22 문서). */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ErrorResponseWriter writer;

  public RestAccessDeniedHandler(ErrorResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    writer.write(
        request, response, classify(SecurityContextHolder.getContext().getAuthentication()));
  }

  static ErrorCode classify(Authentication auth) {
    if (auth != null
        && auth.getAuthorities().stream()
            .anyMatch(a -> UserIdAuthenticationConverter.MFA_REQUIRED.equals(a.getAuthority()))) {
      return ErrorCode.MFA_REQUIRED;
    }
    return ErrorCode.FORBIDDEN;
  }
}
