package app.wearly.auth.application.port.in.dto;

/**
 * IdP가 돌려보낸 콜백 (GET /auth/sso/{provider}/callback).
 *
 * @param code 인가 코드 (오류면 null)
 * @param state 시작 때 보낸 state
 * @param error IdP 오류 코드 (access_denied 등, 없으면 null)
 * @param issuer 인가 응답의 iss (RFC 9207, 보내는 IdP만. 있으면 설정의 issuer와 같아야 한다)
 * @param cookieState 시작 때 브라우저에 둔 state 쿠키 (같은 브라우저인지 확인, 없으면 null)
 * @param client 요청한 기기 (로그인 거부 기록용)
 */
public record SsoCallbackDto(
    String providerId,
    String code,
    String state,
    String error,
    String issuer,
    String cookieState,
    ClientDto client) {

  @Override
  public String toString() {
    return "SsoCallbackDto[providerId=" + providerId + ", error=" + error + "]";
  }
}
