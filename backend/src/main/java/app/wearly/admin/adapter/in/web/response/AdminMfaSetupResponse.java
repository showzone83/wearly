package app.wearly.admin.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.MfaSetupDto;

/** POST /admin/me/mfa/setup: secret(base32, 수동 입력용), otpauthUrl(QR) */
public record AdminMfaSetupResponse(String secret, String otpauthUrl) {

  public static AdminMfaSetupResponse from(MfaSetupDto d) {
    return new AdminMfaSetupResponse(d.secret(), d.otpauthUrl());
  }

  @Override
  public String toString() {
    return "AdminMfaSetupResponse[secret=***]";
  }
}
