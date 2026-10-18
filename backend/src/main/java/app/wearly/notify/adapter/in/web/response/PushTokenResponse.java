package app.wearly.notify.adapter.in.web.response;

import app.wearly.notify.application.port.in.dto.PushRegistrationDto;

/**
 * PUT /me/push-token 응답.
 *
 * @param platform ios · android
 * @param serverPush 서버가 이 기기에 D-1 알림을 보낸다 (true면 앱은 로컬 D-1 알림을 끈다)
 * @param dayBeforeTime 서버 D-1 작업 시각 "HH:mm" (한국 시간)
 */
public record PushTokenResponse(
    String deviceId, String platform, boolean serverPush, String dayBeforeTime) {

  public static PushTokenResponse from(PushRegistrationDto d) {
    return new PushTokenResponse(
        d.deviceId(), d.platform().wire(), d.serverPush(), d.dayBeforeTime().toString());
  }
}
