package app.wearly.user;

import app.wearly.common.Ids;
import app.wearly.common.Times;

/** role은 관리자 사이트가 확인한다 (USER | ADMIN). 앱은 무시해도 된다. */
public record UserDto(String id, String email, String name, String role, String createdAt) {

    public static UserDto from(User user) {
        return new UserDto(Ids.format(user.getId()), user.getEmail(), user.getName(), user.getRole(),
                Times.iso(user.getCreatedAt()));
    }
}
