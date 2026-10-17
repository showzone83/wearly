package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.AuthResultDto;

/** 가입·로그인·갱신 응답 (05 문서 6장 AuthResponse) */
public record AuthResponse(
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    long refreshTokenExpiresIn,
    AuthUserResponse user) {

  public static AuthResponse from(AuthResultDto r) {
    return new AuthResponse(
        r.accessToken(),
        r.accessTokenExpiresIn(),
        r.refreshToken(),
        r.refreshTokenExpiresIn(),
        AuthUserResponse.from(r.user()));
  }
}
