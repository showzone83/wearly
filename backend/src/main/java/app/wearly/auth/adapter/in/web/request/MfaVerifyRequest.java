package app.wearly.auth.adapter.in.web.request;

import app.wearly.auth.application.port.in.dto.MfaVerifyDto;

/** POST /auth/mfa/verify: MFA 대기 토큰 + code(TOTP 6자리) 또는 recoveryCode */
public record MfaVerifyRequest(String mfaToken, String code, String recoveryCode) {

  public MfaVerifyDto toDto() {
    return new MfaVerifyDto(mfaToken, code, recoveryCode);
  }

  @Override
  public String toString() {
    return "MfaVerifyRequest[mfaToken=***, code=***, recoveryCode=***]";
  }
}
