package app.wearly.invite.domain;

import java.time.Duration;

/**
 * 초대한 이력 남기기 한도 (doc/25 6장). 연속 클릭·스크립트로 이력이 무한히 쌓이지 않게, 같은 회원·같은 방법은 {@link #COOLDOWN}에 한 번, 회원당
 * 하루(한국 날짜) {@link #DAILY_LIMIT}건까지만 남긴다. 넘으면 기록하지 않고 요청은 그대로 성공(204)한다.
 */
public final class InviteSharePolicy {

  public static final Duration COOLDOWN = Duration.ofSeconds(60);
  public static final int DAILY_LIMIT = 30;

  private InviteSharePolicy() {}

  /** 기록해도 되는지 확인한 결과 */
  public enum Gate {
    /** 기록한다 */
    RECORD,
    /** 같은 방법을 방금 남겼다 */
    COOLDOWN,
    /** 오늘 한도에 처음 걸렸다 (경고 로그 한 번) */
    DAILY_LIMIT_FIRST,
    /** 오늘 한도를 이미 넘었다 */
    DAILY_LIMIT
  }
}
