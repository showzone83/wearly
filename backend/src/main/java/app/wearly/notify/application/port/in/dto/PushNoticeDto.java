package app.wearly.notify.application.port.in.dto;

import java.util.Map;

/**
 * 회원에게 보낼 알림 한 건 (D-1 알림이 아닌 일반 알림).
 *
 * @param data 앱이 알림을 눌렀을 때 쓰는 값 (type 등)
 * @param channelId 안드로이드 알림 채널 (앱이 같은 ID로 만든 것)
 */
public record PushNoticeDto(
    String title, String body, Map<String, String> data, String channelId) {}
