package app.wearly.auth.application.port.in.dto;

import java.util.Map;

/**
 * SSO 시작·콜백의 302 응답 내용.
 *
 * @param location 보낼 주소. 성공이면 완성된 주소(IdP authorize 또는 관리자 사이트 + code), 오류면 관리자 사이트 주소(오류 값은 웹 어댑터가
 *     붙인다)
 * @param stateCookie 시작 성공 때 브라우저에 둘 state 쿠키 값 (그 밖은 null)
 * @param error 오류 코드 (ErrorCode 이름, 성공이면 null)
 * @param status 오류의 HTTP 상태 (성공이면 0)
 * @param params 오류 params (없으면 빈 값)
 */
public record SsoRedirectDto(
    String location, String stateCookie, String error, int status, Map<String, Object> params) {

  public SsoRedirectDto {
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  public static SsoRedirectDto to(String location) {
    return new SsoRedirectDto(location, null, null, 0, Map.of());
  }

  public boolean failed() {
    return error != null;
  }
}
