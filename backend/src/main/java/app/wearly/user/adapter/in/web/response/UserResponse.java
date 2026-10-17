package app.wearly.user.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.user.application.port.in.dto.UserAccountDto;

/** GET /users/me. role은 USER | ADMIN. 앱은 무시해도 되고 관리자 사이트는 ADMIN인지 확인한다 (07 문서 2장) */
public record UserResponse(String id, String email, String name, String role, String createdAt) {

  public static UserResponse from(UserAccountDto user) {
    return new UserResponse(
        Ids.format(user.id()), user.email(), user.name(), user.role(), Times.iso(user.createdAt()));
  }
}
