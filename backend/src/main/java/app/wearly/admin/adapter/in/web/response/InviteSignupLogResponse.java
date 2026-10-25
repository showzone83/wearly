package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.InviteSignupLogDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/**
 * GET /admin/invites/signups 항목 (doc/25). result = LINKED · UNKNOWN_CODE. referrer*는 추천인 (없으면
 * null). enteredCode = 가입자가 입력한 코드 (공백·하이픈을 빼고 대문자로 정리한 값)
 */
public record InviteSignupLogResponse(
    String id,
    String createdAt,
    String userId,
    String userEmail,
    String userName,
    String enteredCode,
    String result,
    String referrerId,
    String referrerEmail,
    String referrerName) {

  public static InviteSignupLogResponse from(InviteSignupLogDto d) {
    return new InviteSignupLogResponse(
        Ids.format(d.id()),
        Times.iso(d.createdAt()),
        Ids.format(d.userId()),
        d.userEmail(),
        d.userName(),
        d.enteredCode(),
        d.result(),
        Ids.format(d.referrerId()),
        d.referrerEmail(),
        d.referrerName());
  }
}
