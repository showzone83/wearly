package app.wearly.notify.adapter.in.web.request;

import app.wearly.notify.application.port.in.dto.RegisterPushTokenDto;

/**
 * PUT /me/push-token 본문 { token, platform: ios | android, deviceId }. 검증은 유스케이스(PushTokenRules)가
 * 한다.
 *
 * @param token Expo 푸시 토큰 (ExponentPushToken[…])
 * @param deviceId 앱이 처음 실행할 때 만든 기기 ID (1~64자, 영문·숫자·. _ : -)
 */
public record PushTokenRequest(String token, String platform, String deviceId) {

  public RegisterPushTokenDto toDto() {
    return new RegisterPushTokenDto(token, platform, deviceId);
  }
}
