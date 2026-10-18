package app.wearly.auth.application.port.in;

import java.util.Optional;

/** 관리자 컨텍스트가 쓰는 계정 관리: 시작 시 관리자 만들기, AI 키 변경 전 비밀번호 재확인, 관리자 비밀번호 변경 */
public interface AccountUseCase {

  /** 이메일(정규화된 값)로 가입한 회원 ID */
  Optional<Long> findUserIdByEmail(String email);

  /** 회원 + 이메일 자격 증명을 만든다. @return 회원 ID */
  long createAccount(String email, String rawPassword, String name, String role);

  /** 관리자 재확인: 그 회원의 이메일 비밀번호가 맞는지 (자격 증명이 없거나 비면 false) */
  boolean passwordMatches(long userId, String rawPassword);

  /**
   * 비밀번호 변경. 현재 비밀번호가 틀리면 403 REAUTH_FAILED, 새 비밀번호가 규칙(8~64자, 영문·숫자)에 맞지 않으면 400
   * VALIDATION_FAILED(newPassword). 바꾸면 keepSessionId(지금 세션)만 남기고 그 회원의 다른 세션을 모두 끊는다.
   *
   * @return 끊은 세션 수
   */
  int changePassword(long userId, String keepSessionId, String currentPassword, String newPassword);
}
