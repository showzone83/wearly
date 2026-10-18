package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.AuthenticatedDto;
import java.util.Optional;

/**
 * 매 요청 Access 토큰 서명·만료 확인 뒤 세션·회원 상태 확인 (16 문서 3장). 세션이 끊겼거나(로그아웃·강제 로그아웃·정지·탈퇴) 회원이 정지·탈퇴면 빈 값 →
 * 401 TOKEN_INVALID
 */
public interface AuthenticationUseCase {

  /**
   * 세션이 살아 있고 그 회원의 것이며 회원이 쓸 수 있는(ACTIVE) 상태면 현재 역할 (USER | ADMIN)과, 관리자인데 이 세션이 2단계 인증을 거치지 않아
   * 관리자 API를 쓸 수 없는지(22 문서). sessionId가 없으면(sid 없는 옛 토큰) 빈 값
   */
  Optional<AuthenticatedDto> authenticate(long userId, String sessionId);
}
