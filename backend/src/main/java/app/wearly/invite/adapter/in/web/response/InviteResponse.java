package app.wearly.invite.adapter.in.web.response;

import app.wearly.invite.application.port.in.dto.InviteDto;

/**
 * GET /users/me/invite 응답.
 *
 * @param inviteCode 정리한 8자 (앱이 XXXX-XXXX로 보인다)
 * @param invitedCount 이 코드로 가입한 친구 수
 */
public record InviteResponse(String inviteCode, long invitedCount) {

  public static InviteResponse from(InviteDto d) {
    return new InviteResponse(d.code(), d.invitedCount());
  }
}
