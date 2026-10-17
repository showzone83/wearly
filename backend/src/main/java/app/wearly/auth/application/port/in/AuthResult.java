package app.wearly.auth.application.port.in;

import app.wearly.user.application.port.in.UserAccount;

/** 가입·로그인·갱신 결과 (05 문서 6장 AuthResponse의 값) */
public record AuthResult(
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    long refreshTokenExpiresIn,
    UserAccount user) {}
