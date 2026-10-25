package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.InviteShareLogDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/** GET /admin/invites/shares 항목 (doc/25). channel = SHARE · SMS · COPY */
public record InviteShareLogResponse(
    String id, String createdAt, String userId, String userEmail, String userName, String channel) {

  public static InviteShareLogResponse from(InviteShareLogDto d) {
    return new InviteShareLogResponse(
        Ids.format(d.id()),
        Times.iso(d.createdAt()),
        Ids.format(d.userId()),
        d.userEmail(),
        d.userName(),
        d.channel());
  }
}
