package app.wearly.user.adapter.in.web;

import app.wearly.common.domain.Ids;
import app.wearly.common.domain.Times;
import app.wearly.user.application.port.in.UserAccount;

/** role은 USER | ADMIN. 앱은 무시해도 되고 관리자 사이트는 ADMIN인지 확인한다 (07 문서 2장) */
public record UserDto(String id, String email, String name, String role, String createdAt) {

    public static UserDto from(UserAccount user) {
        return new UserDto(Ids.format(user.id()), user.email(), user.name(), user.role(), Times.iso(user.createdAt()));
    }
}
