package app.wearly.user.application.port.in;

import java.util.Optional;

/** 회원 계정 유스케이스. 인증(가입·로그인)과 관리자(회원 관리·시작 시 관리자)도 이 포트로 부른다 */
public interface UserUseCase {

  Optional<UserAccount> find(long userId);

  /** 없으면 404 */
  UserAccount get(long userId);

  /** 새 회원 (상태 ACTIVE). 이름은 앞뒤 공백을 지우지 않고 받은 그대로 저장한다 */
  UserAccount create(String email, String name, String role);

  /** 상태·역할 바꾸기 (검증은 부르는 쪽 책임) */
  UserAccount changeAccess(long userId, String status, String role);

  /**
   * 탈퇴: 회원 행을 지우면 FK ON DELETE CASCADE로 자격 증명·Refresh 토큰·프로필·옷장·AI 기록이 모두 지워진다. 남은 Access 토큰은 회원
   * 조회에서 걸러져 401 TOKEN_INVALID가 된다.
   */
  void delete(long userId);
}
