package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.LoginResultDto;

/** POST /auth/login 응답: 토큰({@link AuthResponse}) 또는 2단계 인증 대기({@link MfaChallengeResponse}) */
public sealed interface LoginResponse permits AuthResponse, MfaChallengeResponse {

  static LoginResponse from(LoginResultDto r) {
    return r.mfa() != null ? MfaChallengeResponse.from(r.mfa()) : AuthResponse.from(r.tokens());
  }
}
