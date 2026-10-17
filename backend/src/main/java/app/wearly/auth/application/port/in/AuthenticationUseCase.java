package app.wearly.auth.application.port.in;

import java.util.Optional;

/** 매 요청 Access 토큰 확인 뒤 회원 상태 확인 (탈퇴·정지면 빈 값 → 401 TOKEN_INVALID) */
public interface AuthenticationUseCase {

  /** 쓸 수 있는(ACTIVE) 회원이면 현재 역할 (USER | ADMIN) */
  Optional<String> activeUserRole(long userId);
}
