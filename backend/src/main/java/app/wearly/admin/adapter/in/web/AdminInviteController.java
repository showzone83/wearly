package app.wearly.admin.adapter.in.web;

import app.wearly.admin.adapter.in.web.response.InviteShareLogResponse;
import app.wearly.admin.adapter.in.web.response.InviteSignupLogResponse;
import app.wearly.admin.adapter.in.web.response.PageResponse;
import app.wearly.admin.application.port.in.AdminInviteUseCase;
import app.wearly.admin.application.port.in.dto.PagingDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 친구 초대 이력 API (doc/25, 07 문서 4장). ADMIN 전용, 읽기만 */
@RestController
@RequestMapping("/api/v1/admin/invites")
public class AdminInviteController {

  private final AdminInviteUseCase invites;

  public AdminInviteController(AdminInviteUseCase invites) {
    this.invites = invites;
  }

  /** 초대 코드 가입 이력 { items, page, size, total }. result: LINKED · UNKNOWN_CODE */
  @GetMapping("/signups")
  public PageResponse<InviteSignupLogResponse> signups(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        invites.signups(q, result, PagingDto.of(page, size)), InviteSignupLogResponse::from);
  }

  /** 초대한 이력 { items, page, size, total }. channel: SHARE · SMS · COPY */
  @GetMapping("/shares")
  public PageResponse<InviteShareLogResponse> shares(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String channel,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return PageResponse.from(
        invites.shares(q, channel, PagingDto.of(page, size)), InviteShareLogResponse::from);
  }
}
