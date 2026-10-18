package app.wearly.common.web;

import app.wearly.common.error.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 정한다: X-Request-Id 헤더가 형식에 맞으면 그 값, 아니면 새로 만든다. MDC(traceId)에 넣고 응답 헤더 X-Request-Id로
 * 돌려준다. 보안 필터보다 먼저 돌아 401·403에도 붙는다. /error로 다시 들어온 요청(ERROR dispatch)도 같은 값을 쓴다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    withTraceId(request, response, chain);
  }

  @Override
  protected void doFilterNestedErrorDispatch(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    withTraceId(request, response, chain);
  }

  private static void withTraceId(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = (String) request.getAttribute(ATTRIBUTE);
    if (traceId == null) {
      String header = request.getHeader(TraceIds.HEADER);
      traceId = TraceIds.isValid(header) ? header : TraceIds.generate();
      request.setAttribute(ATTRIBUTE, traceId);
    }
    if (!response.isCommitted()) {
      response.setHeader(TraceIds.HEADER, traceId);
    }
    String previous = MDC.get(TraceIds.MDC_KEY);
    MDC.put(TraceIds.MDC_KEY, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) {
        MDC.remove(TraceIds.MDC_KEY);
      } else {
        MDC.put(TraceIds.MDC_KEY, previous);
      }
    }
  }
}
