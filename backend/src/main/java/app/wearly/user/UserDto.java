package app.wearly.user;

import app.wearly.common.Ids;
import app.wearly.common.Times;

public record UserDto(String id, String email, String name, String createdAt) {

    public static UserDto from(User user) {
        return new UserDto(Ids.format(user.getId()), user.getEmail(), user.getName(), Times.iso(user.getCreatedAt()));
    }
}
