package app.wearly.user;

import app.wearly.common.Ids;
import app.wearly.common.Times;

/** role은 USER | ADMIN. 앱은 무시해도 되고 관리자 사이트는 ADMIN인지 확인한다 (07 문서 2장) */
public record UserDto(String id, String email, String name, String role, String createdAt) {

    public static UserDto from(User user) {
        return new UserDto(Ids.format(user.getId()), user.getEmail(), user.getName(), user.getRole(),
                Times.iso(user.getCreatedAt()));
    }
}
