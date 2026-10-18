package app.wearly.auth.adapter.in.web.request;

/** POST /auth/mfa/setup: MFA 대기 토큰 */
public record MfaTokenRequest(String mfaToken) {

  @Override
  public String toString() {
    return "MfaTokenRequest[mfaToken=***]";
  }
}
