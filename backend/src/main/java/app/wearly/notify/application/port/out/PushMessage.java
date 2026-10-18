package app.wearly.notify.application.port.out;

import java.util.Map;

/**
 * 푸시 한 건 (기기 하나). data는 앱이 알림을 눌렀을 때 쓰는 값이다.
 *
 * @param data 예: { type: "plan-d1", planId: "기기 예정 ID", serverId: "12", date: "2026-10-19" }
 */
public record PushMessage(String token, String title, String body, Map<String, String> data) {}
