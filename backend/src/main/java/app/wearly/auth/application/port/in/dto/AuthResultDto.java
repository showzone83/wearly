package app.wearly.auth.application.port.in.dto;

import app.wearly.user.application.port.in.dto.UserAccountDto;

/** 가입·로그인·갱신 결과 (05 문서 6장 AuthResponse의 값) */
public record AuthResultDto(
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    long refreshTokenExpiresIn,
    UserAccountDto user) {}
