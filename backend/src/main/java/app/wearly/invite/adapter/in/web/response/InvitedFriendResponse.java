package app.wearly.invite.adapter.in.web.response;

import app.wearly.common.web.Times;
import app.wearly.invite.application.port.in.dto.InvitedFriendDto;

/** GET /users/me/invite/friends 항목: 초대로 가입한 친구의 이름과 가입 시각 */
public record InvitedFriendResponse(String name, String joinedAt) {

  public static InvitedFriendResponse from(InvitedFriendDto d) {
    return new InvitedFriendResponse(d.name(), Times.iso(d.joinedAt()));
  }
}
