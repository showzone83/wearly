package app.wearly.auth.adapter.in.web.response;

import app.wearly.auth.application.port.in.dto.MfaChallengeDto;

/**
 * 관리자 사이트 로그인에서 2단계 인증이 필요할 때 (22 문서): 토큰 대신 MFA 대기 토큰(5분, 관리자 API에 쓸 수 없음). 다음은 enrolled가 false면
 * POST /auth/mfa/setup, true면 POST /auth/mfa/verify
 */
public record MfaChallengeResponse(Mfa mfa, String mfaToken, long mfaTokenExpiresIn)
    implements LoginResponse {

  /**
   * @param required 이 로그인에 2단계 인증이 필요함 (이 응답에서는 늘 true)
   * @param enrolled TOTP를 이미 등록했는지
   */
  public record Mfa(boolean required, boolean enrolled) {}

  public static MfaChallengeResponse from(MfaChallengeDto d) {
    return new MfaChallengeResponse(
        new Mfa(true, d.enrolled()), d.mfaToken(), d.expiresInSeconds());
  }
}
