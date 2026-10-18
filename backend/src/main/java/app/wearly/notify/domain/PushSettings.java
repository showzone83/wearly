package app.wearly.notify.domain;

import java.time.LocalTime;

/**
 * 서버 푸시 설정 (wearly.notify.*).
 *
 * @param enabled 서버 푸시 사용 (기본 false). false면 토큰은 받아 두지만 D-1 작업이 보내지 않는다
 * @param provider EXPO(Expo Push API) · LOG(로그만 남기는 가짜, 로컬 기본)
 * @param dayBeforeTime D-1 작업 시각 (한국 시간)
 */
public record PushSettings(boolean enabled, Provider provider, LocalTime dayBeforeTime) {

  public enum Provider {
    EXPO,
    LOG
  }

  /** 실제로 기기에 푸시가 가는지 (앱이 로컬 D-1 알림을 꺼도 되는지). LOG는 보내지 않으므로 false */
  public boolean deliversToDevices() {
    return enabled && provider == Provider.EXPO;
  }
}
