package app.wearly.auth.adapter.in.web;

import app.wearly.auth.application.port.in.dto.ClientDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * 인증 요청의 기기 정보 (16 문서 4장): {@code X-Wearly-Client}(app | admin | web), User-Agent, IP({@code
 * X-Forwarded-For} 첫 값 또는 원격 주소). 값 정리·추정은 도메인(ClientInfo)이 한다
 */
final class ClientHeaders {

  static final String CLIENT = "X-Wearly-Client";
  static final String FORWARDED_FOR = "X-Forwarded-For";

  private ClientHeaders() {}

  static ClientDto from(HttpServletRequest request) {
    return new ClientDto(
        request.getHeader(CLIENT), request.getHeader(HttpHeaders.USER_AGENT), ip(request));
  }

  static String ip(HttpServletRequest request) {
    String forwarded = request.getHeader(FORWARDED_FOR);
    if (forwarded != null && !forwarded.isBlank()) {
      String first = forwarded.split(",", 2)[0].strip();
      if (!first.isEmpty()) {
        return first;
      }
    }
    return request.getRemoteAddr();
  }
}
