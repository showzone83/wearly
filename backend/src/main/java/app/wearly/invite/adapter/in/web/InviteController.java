package app.wearly.invite.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.invite.adapter.in.web.request.InviteShareRequest;
import app.wearly.invite.adapter.in.web.response.InviteResponse;
import app.wearly.invite.adapter.in.web.response.InvitedFriendResponse;
import app.wearly.invite.application.port.in.InviteUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 친구 초대 (doc/25, 로그인 필요): 내 초대 코드, 초대한 이력 남기기, 초대로 가입한 친구 */
@RestController
@RequestMapping("/api/v1/users/me/invite")
public class InviteController {

  private final InviteUseCase invites;

  public InviteController(InviteUseCase invites) {
    this.invites = invites;
  }

  /** 200 { inviteCode, invitedCount }. 코드가 없으면 만든다 */
  @GetMapping
  public InviteResponse get(@CurrentUser long userId) {
    return InviteResponse.from(invites.myInvite(userId));
  }

  /** 초대한 이력 { channel: SHARE | SMS | COPY } → 204 */
  @PostMapping("/shares")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void share(@CurrentUser long userId, @RequestBody InviteShareRequest request) {
    invites.recordShare(userId, request.channel());
  }

  /** 200 [{ name, joinedAt }] 최근 가입순, 최대 100명 */
  @GetMapping("/friends")
  public List<InvitedFriendResponse> friends(@CurrentUser long userId) {
    return invites.invitedFriends(userId).stream().map(InvitedFriendResponse::from).toList();
  }
}
