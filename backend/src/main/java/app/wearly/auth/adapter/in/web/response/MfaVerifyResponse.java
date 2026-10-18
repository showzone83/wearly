package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.MfaVerifyResultDto;
import java.util.List;

/**
 * POST /auth/mfa/verify 응답 (22 문서): AuthResponse와 같은 토큰 필드 + recoveryCodes(처음 등록할 때만 10개, 그 밖에는
 * null) + recoveryCodesLeft(쓰지 않은 복구 코드 수)
 */
public record MfaVerifyResponse(
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    long refreshTokenExpiresIn,
    AuthUserResponse user,
    List<String> recoveryCodes,
    int recoveryCodesLeft) {

  public static MfaVerifyResponse from(MfaVerifyResultDto r) {
    return new MfaVerifyResponse(
        r.tokens().accessToken(),
        r.tokens().accessTokenExpiresIn(),
        r.tokens().refreshToken(),
        r.tokens().refreshTokenExpiresIn(),
        AuthUserResponse.from(r.tokens().user()),
        r.recoveryCodes(),
        r.recoveryCodesLeft());
  }
}
