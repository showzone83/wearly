package app.wearly.user;

import app.wearly.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;

    public UserController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    @GetMapping
    public UserDto me(@CurrentUser long userId) {
        return userService.me(userId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentUser long userId) {
        userService.delete(userId);
    }

    @GetMapping("/profile")
    public ProfileDto profile(@CurrentUser long userId) {
        return profileService.get(userId);
    }

    @PutMapping("/profile")
    public ProfileDto putProfile(@CurrentUser long userId, @Valid @RequestBody ProfileDto profile) {
        return profileService.put(userId, profile);
    }
}
