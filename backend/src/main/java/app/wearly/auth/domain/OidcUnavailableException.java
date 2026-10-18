package app.wearly.auth.domain;

/**
 * IdP에 닿지 못함(discovery·토큰·JWKS 주소 연결 실패, 5xx, 모양이 틀린 응답). 서비스가 502 SSO_PROVIDER_UNAVAILABLE로 바꾼다
 */
public class OidcUnavailableException extends RuntimeException {

  public OidcUnavailableException(String message) {
    super(message);
  }

  public OidcUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
