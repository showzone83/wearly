package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.AuthResultDto;
import app.wearly.auth.application.port.in.dto.ClientDto;
import app.wearly.auth.application.port.in.dto.LoginDto;
import app.wearly.auth.application.port.in.dto.SignupDto;

/** 가입·로그인·토큰 갱신·로그아웃 (05 문서 6장). 가입·로그인은 새 로그인 세션을 만든다 (16 문서) */
public interface AuthUseCase {

  AuthResultDto signup(SignupDto command, ClientDto client);

  AuthResultDto login(LoginDto command, ClientDto client);

  /** 토큰이 없거나 비어 있어도 401 REFRESH_INVALID */
  AuthResultDto refresh(String rawRefreshToken, ClientDto client);

  /** 그 토큰의 세션을 끊는다. 토큰이 없거나 틀려도 조용히 끝낸다 */
  void logout(String rawRefreshToken, ClientDto client);
}
