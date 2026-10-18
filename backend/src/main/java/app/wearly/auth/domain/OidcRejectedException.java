package app.wearly.auth.domain;

/**
 * IdP 응답을 믿을 수 없음: 토큰 교환 거절(invalid_grant 등), ID 토큰 서명·iss·aud·exp·nonce가 맞지 않음. 서비스가 401
 * SSO_TOKEN_INVALID로 바꾼다. 메시지는 로그용이다.
 */
public class OidcRejectedException extends RuntimeException {

  /** 실패 이유 (로그·테스트용 짧은 이름) */
  public enum Reason {
    TOKEN_REQUEST,
    NO_ID_TOKEN,
    MALFORMED,
    SIGNATURE,
    ISSUER,
    AUDIENCE,
    EXPIRED,
    ISSUED_IN_FUTURE,
    NONCE,
    SUBJECT
  }

  private final Reason reason;

  public OidcRejectedException(Reason reason, String message) {
    super(reason + ": " + message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
