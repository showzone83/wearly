package app.wearly.auth.application.port.in.dto;

/** 로그인 결과: 토큰(tokens) 또는 2단계 인증 대기(mfa) 중 하나만 있다 */
public record LoginResultDto(AuthResultDto tokens, MfaChallengeDto mfa) {

  public static LoginResultDto of(AuthResultDto tokens) {
    return new LoginResultDto(tokens, null);
  }

  public static LoginResultDto pending(MfaChallengeDto mfa) {
    return new LoginResultDto(null, mfa);
  }
}
