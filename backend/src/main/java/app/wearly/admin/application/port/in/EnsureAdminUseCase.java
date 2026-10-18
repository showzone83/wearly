package app.wearly.admin.application.port.in;

import java.util.Optional;

/**
 * 시작 시 관리자 계정 만들기 (07 문서 2장, 22 문서 4.1). 관리자(ADMIN)가 한 명도 없을 때만 설정의 이메일로 관리자를 새로 만든다(이름 기본 "관리자").
 * 그 이메일로 이미 가입한 회원이 있으면 올리지 않는다(경고 로그). 관리자가 이미 있으면 설정을 쓰지 않는다.
 */
public interface EnsureAdminUseCase {

  /**
   * @return 새로 만든 관리자 회원 ID (만들지 않았으면 빈 값)
   */
  Optional<Long> ensureAdmin(String email, String password, String name);
}
