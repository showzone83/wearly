package app.wearly.user.application.port.in;

import app.wearly.user.application.port.in.dto.UserAccountDto;
import java.util.Optional;

/** 회원 계정 유스케이스. 인증(가입·로그인)과 관리자(회원 관리·시작 시 관리자)도 이 포트로 부른다 */
public interface UserUseCase {

  Optional<UserAccountDto> find(long userId);

  /** 없으면 404 */
  UserAccountDto get(long userId);

  /**
   * 새 회원 (상태 ACTIVE). 이름은 앞뒤 공백을 지우지 않고 받은 그대로 저장한다. 이름(1~20자)·역할(USER·ADMIN)이 틀리면 400
   * VALIDATION_FAILED (도메인 규칙)
   */
  UserAccountDto create(String email, String name, String role);

  /**
   * 상태·역할 바꾸기. 허용 값(ACTIVE·SUSPENDED, USER·ADMIN)은 도메인이 확인한다 (틀리면 400 VALIDATION_FAILED). "자기 자신은
   * 정지할 수 없음" 같은 관리자 정책은 부르는 쪽(admin)이 정한다
   */
  UserAccountDto changeAccess(long userId, String status, String role);

  /**
   * 탈퇴: 회원 행을 지우면 FK ON DELETE CASCADE로 자격 증명·로그인 이력·프로필·옷장·AI 기록이 모두 지워진다. DB 밖의 로그인 세션(Valkey)은
   * {@link UserDeletedListener}(auth)가 지운다. 남은 Access 토큰은 세션 확인에서 걸러져 401 TOKEN_INVALID가 된다.
   */
  void delete(long userId);
}
