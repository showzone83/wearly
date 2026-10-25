package app.wearly.auth.domain;

import java.util.Map;

/**
 * 비밀번호 변경 알림 푸시 한 건 (28 문서 2단계). 앱은 data.type으로 알림을 알아보고 누르면 비밀번호 변경 화면을 연다.
 *
 * @param data 앱이 알림을 눌렀을 때 쓰는 값 (type)
 * @param channelId 안드로이드 알림 채널 (앱이 같은 ID로 만든 것)
 */
public record PasswordReminderNotice(
    String title, String body, Map<String, String> data, String channelId) {

  /** 앱이 알림을 알아보는 data.type */
  public static final String TYPE = "password-change-due";

  /** 안드로이드 알림 채널 (계정 알림) */
  public static final String CHANNEL_ID = "account";

  /** intervalDays: 변경 주기 일수 (본문 "N일이 지났어요") */
  public static PasswordReminderNotice of(long intervalDays) {
    return new PasswordReminderNotice(
        "비밀번호를 바꿔 주세요",
        "비밀번호를 바꾼 지 " + intervalDays + "일이 지났어요. 눌러서 바꾸거나 " + intervalDays + "일 연장할 수 있어요.",
        Map.of("type", TYPE),
        CHANNEL_ID);
  }
}
