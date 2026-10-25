package app.wearly.support;

import app.wearly.invite.application.port.in.InviteUseCase;
import app.wearly.invite.application.port.in.dto.InviteDto;
import app.wearly.invite.application.port.in.dto.InvitedFriendDto;
import java.util.ArrayList;
import java.util.List;

/** 서비스 단위 테스트용 InviteUseCase. 가입 때 넘긴 초대 코드만 기록한다 */
public class FakeInvites implements InviteUseCase {

  /** recordSignup으로 받은 (새 회원 ID, 입력 코드) */
  public final List<Signup> signups = new ArrayList<>();

  public record Signup(long userId, String enteredCode) {}

  @Override
  public InviteDto myInvite(long userId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void recordShare(long userId, String channel) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<InvitedFriendDto> invitedFriends(long userId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void recordSignup(long newUserId, String enteredCode) {
    signups.add(new Signup(newUserId, enteredCode));
  }
}
