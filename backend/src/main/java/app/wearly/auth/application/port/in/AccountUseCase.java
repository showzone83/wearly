package app.wearly.auth.application.port.in;

import java.util.Optional;

/** 관리자 컨텍스트가 쓰는 계정 관리: 시작 시 관리자 만들기, 정지 시 토큰 폐기 */
public interface AccountUseCase {

  /** 이메일(정규화된 값)로 가입한 회원 ID */
  Optional<Long> findUserIdByEmail(String email);

  /** 회원 + 이메일 자격 증명을 만든다. @return 회원 ID */
  long createAccount(String email, String rawPassword, String name, String role);

  /** 관리자 재확인: 그 회원의 이메일 비밀번호가 맞는지 (자격 증명이 없거나 비면 false) */
  boolean passwordMatches(long userId, String rawPassword);

  /** 그 회원의 살아 있는 Refresh 토큰을 모두 폐기한다 */
  void revokeAllRefreshTokens(long userId);
}
