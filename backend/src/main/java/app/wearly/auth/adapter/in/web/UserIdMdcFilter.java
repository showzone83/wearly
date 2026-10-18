package app.wearly.auth.adapter.in.web;

import app.wearly.common.error.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인증된 회원 ID를 MDC(userId)에 넣는다. 로그 패턴과 오류 로그가 쓴다. 보안 필터 체인에서 Bearer 인증 바로 뒤에 둔다 (SecurityConfig).
 */
public class UserIdMdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)) {
      chain.doFilter(request, response);
      return;
    }
    MDC.put(TraceIds.USER_MDC_KEY, jwt.getName());
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(TraceIds.USER_MDC_KEY);
    }
  }
}
