package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.LoginResultDto;
import app.wearly.auth.application.port.in.dto.SignupDto;

/** 가입·로그인·토큰 갱신·로그아웃 (05 문서 6장). 가입·로그인은 새 로그인 세션을 만든다 (16 문서) */
public interface AuthUseCase {

  AuthResultDto signup(SignupDto command, ClientDto client);

  /**
   * 토큰을 준다. 단 관리자 사이트(X-Wearly-Client: admin)에서 2단계 인증이 필요한 관리자가 로그인하면 토큰 대신 MFA 대기 토큰을 준다 (22 문서).
   * 비밀번호가 틀리면 그 회원의 로그인 이력에 LOGIN_FAILED를 남긴다(없는 이메일은 남기지 않는다).
   */
  LoginResultDto login(LoginDto command, ClientDto client);

  /** 토큰이 없거나 비어 있어도 401 REFRESH_INVALID */
  AuthResultDto refresh(String rawRefreshToken, ClientDto client);

  /** 그 토큰의 세션을 끊는다. 토큰이 없거나 틀려도 조용히 끝낸다 */
  void logout(String rawRefreshToken, ClientDto client);
}
