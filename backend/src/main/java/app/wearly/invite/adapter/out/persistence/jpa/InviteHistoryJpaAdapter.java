package app.wearly.invite.adapter.out.persistence.jpa;

import app.wearly.invite.application.port.out.InviteHistoryPort;
import app.wearly.invite.domain.InviteChannel;
import app.wearly.invite.domain.InviteSignupResult;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** InviteHistoryPort 구현 (JPA) */
@Component
class InviteHistoryJpaAdapter implements InviteHistoryPort {

  private final InviteShareJpaRepository shares;
  private final InviteSignupJpaRepository signups;

  InviteHistoryJpaAdapter(InviteShareJpaRepository shares, InviteSignupJpaRepository signups) {
    this.shares = shares;
    this.signups = signups;
  }

  @Override
  public void recordShare(long userId, InviteChannel channel, Instant now) {
    shares.save(new InviteShareEntity(userId, channel.name(), now));
  }

  @Override
  public void recordSignup(
      long userId, Long referrerId, String enteredCode, InviteSignupResult result, Instant now) {
    signups.save(new InviteSignupEntity(userId, referrerId, enteredCode, result.name(), now));
  }
}
