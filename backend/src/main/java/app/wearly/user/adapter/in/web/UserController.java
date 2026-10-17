package app.wearly.user.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.user.adapter.in.web.request.ProfileRequest;
import app.wearly.user.adapter.in.web.response.ProfileResponse;
import app.wearly.user.adapter.in.web.response.UserResponse;
import app.wearly.user.application.port.in.ProfileUseCase;
import app.wearly.user.application.port.in.UserUseCase;
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

  private final UserUseCase users;
  private final ProfileUseCase profiles;

  public UserController(UserUseCase users, ProfileUseCase profiles) {
    this.users = users;
    this.profiles = profiles;
  }

  @GetMapping
  public UserResponse me(@CurrentUser long userId) {
    return UserResponse.from(users.get(userId));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMe(@CurrentUser long userId) {
    users.delete(userId);
  }

  @GetMapping("/profile")
  public ProfileResponse profile(@CurrentUser long userId) {
    return ProfileResponse.from(profiles.get(userId));
  }

  @PutMapping("/profile")
  public ProfileResponse putProfile(
      @CurrentUser long userId, @Valid @RequestBody ProfileRequest profile) {
    return ProfileResponse.from(profiles.put(userId, profile.toDto()));
  }
}
