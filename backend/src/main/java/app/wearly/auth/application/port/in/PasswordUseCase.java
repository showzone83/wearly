package app.wearly.auth.application.port.in;

import app.wearly.auth.application.port.in.dto.PasswordStatusDto;

/**
 * 비밀번호 변경과 변경 주기 (28 문서). 앱 회원(/users/me/password)과 관리자(/admin/me/password)가 같이 쓴다. 기한이 지나도 막지 않고
 * 알리기만 한다.
 */
public interface PasswordUseCase {

  /** 변경 주기 상태. 비밀번호가 없는 계정이면 changedAt·dueAt이 null이고 due는 false */
  PasswordStatusDto status(long userId);

  /**
   * 비밀번호 변경. 새 비밀번호가 규칙(8~64자, 영문·숫자)에 맞지 않으면 400 VALIDATION_FAILED(newPassword INVALID_FORMAT),
   * 지금과 같으면 NOT_ALLOWED, 현재 비밀번호가 틀리면 403 REAUTH_FAILED. 바꾸면 keepSessionId(지금 세션)만 남기고 그 회원의 다른 세션을
   * 모두 끊고, 변경 주기를 지금부터 다시 센다. 로그인 이력 PASSWORD_CHANGED.
   *
   * @return 끊은 세션 수
   */
  int change(long userId, String keepSessionId, String currentPassword, String newPassword);

  /**
   * 변경 알림 연장: 지금부터 주기만큼 미룬다. 로그인 이력 PASSWORD_POSTPONED. 기능이 꺼졌거나 비밀번호가 없으면 404.
   *
   * @param sessionId 로그인 이력에 남길 기기 (지금 세션)
   */
  PasswordStatusDto postpone(long userId, String sessionId);
}
