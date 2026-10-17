package app.wearly.auth.adapter.in.web.response;

import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import app.wearly.user.application.port.in.dto.UserAccountDto;

/** AuthResponse.user (user 컨텍스트의 GET /users/me 응답과 같은 모양). role은 USER | ADMIN */
public record AuthUserResponse(
    String id, String email, String name, String role, String createdAt) {

  public static AuthUserResponse from(UserAccountDto u) {
    return new AuthUserResponse(
        Ids.format(u.id()), u.email(), u.name(), u.role(), Times.iso(u.createdAt()));
  }
}
