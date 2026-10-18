package app.wearly.notify.application.port.in.dto;

/**
 * 푸시 토큰 등록 입력 (검증 전).
 *
 * @param platform ios · android
 * @param deviceId 앱이 처음 실행할 때 만든 기기 ID
 */
public record RegisterPushTokenDto(String token, String platform, String deviceId) {}
