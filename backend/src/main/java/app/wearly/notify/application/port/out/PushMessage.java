package app.wearly.notify.application.port.out;

import java.util.Map;

/**
 * 푸시 한 건 (기기 하나). data는 앱이 알림을 눌렀을 때 쓰는 값이다.
 *
 * @param data 예: { type: "plan-d1", planId: "기기 예정 ID", serverId: "12", date: "2026-10-19" }
 * @param channelId 안드로이드 알림 채널 (앱이 같은 ID로 만든 것). null이면 D-1 알림 채널
 * @param ttlSeconds 기기가 꺼져 있을 때 다시 전달을 시도하는 시간(초). null이면 D-1 알림 기본값
 */
public record PushMessage(
    String token,
    String title,
    String body,
    Map<String, String> data,
    String channelId,
    Integer ttlSeconds) {

  /** D-1 알림 채널·기본 유효 시간을 쓰는 푸시 */
  public PushMessage(String token, String title, String body, Map<String, String> data) {
    this(token, title, body, data, null, null);
  }
}
