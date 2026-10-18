package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.MfaSetupDto;

/** TOTP 등록 시작 응답: secret(base32, 수동 입력용), otpauthUrl(QR로 그린다) */
public record MfaSetupResponse(String secret, String otpauthUrl) {

  public static MfaSetupResponse from(MfaSetupDto d) {
    return new MfaSetupResponse(d.secret(), d.otpauthUrl());
  }

  @Override
  public String toString() {
    return "MfaSetupResponse[secret=***]";
  }
}
