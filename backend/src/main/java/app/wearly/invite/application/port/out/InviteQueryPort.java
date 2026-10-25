package app.wearly.invite.application.port.out;

import app.wearly.invite.application.port.out.dto.InvitedFriendRowDto;
import java.util.List;

/** 초대 조회 (MyBatis) */
public interface InviteQueryPort {

  /** 이 회원의 코드로 가입한 친구 수 (LINKED) */
  long countInvited(long referrerId);

  /** 이 회원의 코드로 가입한 친구, 최근 가입순 */
  List<InvitedFriendRowDto> findInvitedFriends(long referrerId, int limit);
}
