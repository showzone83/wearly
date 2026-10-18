package app.wearly.user.application.port.in;

import java.util.Collection;
import java.util.Set;

/** 회원 알림 설정 조회 (notify 컨텍스트의 D-1 작업이 부른다). 설정 값은 프로필의 notify(앱 Profile.notify)다 */
public interface NotifySettingUseCase {

  /** 이 회원들 중 예정 전날 알림(notify.dMinus1)을 켠 활성 회원. 정지된 회원은 빠진다. 프로필이 없으면 기본값(켬) */
  Set<Long> dayBeforeEnabled(Collection<Long> userIds);
}
