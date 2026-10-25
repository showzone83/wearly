package app.wearly.user.adapter.in.web;

import app.wearly.common.web.CurrentUser;
import app.wearly.user.adapter.in.web.request.ProfileRequest;
import app.wearly.user.adapter.in.web.request.WithdrawalRequest;
import app.wearly.user.adapter.in.web.response.ProfileResponse;
import app.wearly.user.adapter.in.web.response.UserResponse;
import app.wearly.user.adapter.in.web.response.WithdrawalResponse;
import app.wearly.user.application.port.in.ProfileUseCase;
import app.wearly.user.application.port.in.UserUseCase;
import app.wearly.user.application.port.in.WithdrawalUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final UserUseCase users;
  private final ProfileUseCase profiles;
  private final WithdrawalUseCase withdrawals;

  public UserController(UserUseCase users, ProfileUseCase profiles, WithdrawalUseCase withdrawals) {
    this.users = users;
    this.profiles = profiles;
    this.withdrawals = withdrawals;
  }

  @GetMapping
  public UserResponse me(@CurrentUser long userId) {
    return UserResponse.from(users.get(userId));
  }

  /**
   * 탈퇴 신청 (doc/27): 200 { requestedAt, purgeAt }. 바로 지우지 않고 탈퇴 예정으로 두며, purgeAt 전에 다시 로그인하면 취소할 수
   * 있다. 로그인 세션은 바로 끊긴다. 즉시 삭제(DELETE /users/me)는 없앴다
   */
  @PostMapping("/withdrawal")
  public WithdrawalResponse withdraw(
      @CurrentUser long userId, @RequestBody WithdrawalRequest request) {
    return WithdrawalResponse.from(withdrawals.withdraw(userId, request.toDto()));
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
