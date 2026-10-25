package app.wearly.invite.application.port.out;

import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSignupResult;
import java.time.Instant;

/** 초대한 이력·초대 코드 가입 이력 쓰기 (tb_invite_share · tb_invite_signup, JPA) */
public interface InviteHistoryPort {

  void recordShare(long userId, InviteChannel channel, Instant now);

  /**
   * @param referrerId 추천인. UNKNOWN_CODE면 null
   * @param enteredCode 정리한 입력 코드 (20자까지)
   */
  void recordSignup(
      long userId, Long referrerId, String enteredCode, InviteSignupResult result, Instant now);
}
