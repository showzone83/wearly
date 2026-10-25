package app.wearly.notify.application.port.in;

import app.wearly.notify.application.port.in.dto.PushRegistrationDto;
import app.wearly.notify.application.port.in.dto.RegisterPushTokenDto;

/** 앱 푸시 토큰 등록·삭제. 회원·기기당 하나, 같은 토큰은 한 회원·기기에만 둔다 */
public interface PushTokenUseCase {

  /**
   * 토큰 등록 (같은 기기면 바꾼다). 응답의 serverPush가 true면 서버가 D-1 알림을 보내므로 앱은 로컬 D-1 알림을 끈다.
   *
   * <p>오류: 400 VALIDATION_FAILED (token·platform·deviceId)
   */
  PushRegistrationDto register(long userId, RegisterPushTokenDto input);

  /**
   * 이 기기의 토큰을 지운다 (로그아웃할 때 앱이 부른다). 없어도 성공이다.
   *
   * <p>오류: 400 VALIDATION_FAILED (deviceId)
   */
  void unregister(long userId, String deviceId);

  /** 회원의 토큰을 모든 기기에서 지운다 (탈퇴 신청, doc/27 — 탈퇴 예정 회원에게 알림이 가지 않게). 없어도 성공이다 */
  void unregisterAll(long userId);
}
