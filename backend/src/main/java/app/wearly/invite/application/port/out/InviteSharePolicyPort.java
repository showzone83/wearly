package app.wearly.invite.application.port.out;

import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSharePolicy.Gate;
import java.time.Duration;
import java.time.LocalDate;

/** 초대한 이력 한도 확인 (Valkey, doc/25 6장). 쿨다운과 하루 상한을 한 번에, 동시에 와도 넘지 않게 확인한다 */
public interface InviteSharePolicyPort {

  /**
   * 같은 회원·같은 방법을 cooldown 안에 남겼으면 COOLDOWN(하루 수는 세지 않음). 오늘 dailyLimit만큼 남겼으면 DAILY_LIMIT_FIRST(처음
   * 걸림)·DAILY_LIMIT. 아니면 RECORD — 하루 수를 하나 더하고 쿨다운을 건다
   *
   * @param day 한국 날짜
   */
  Gate tryAcquire(
      long userId, InviteChannel channel, LocalDate day, Duration cooldown, int dailyLimit);
}
