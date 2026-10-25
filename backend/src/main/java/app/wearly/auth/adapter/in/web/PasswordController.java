package app.wearly.auth.adapter.in.web;

import app.wearly.auth.adapter.in.web.request.ChangePasswordRequest;
import app.wearly.auth.adapter.in.web.response.PasswordStatusResponse;
import app.wearly.auth.application.port.in.PasswordUseCase;
import app.wearly.common.web.CurrentSession;
import app.wearly.common.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 비밀번호 (28 문서, 로그인 필요): 변경 주기 상태, 비밀번호 변경, 변경 알림 연장 */
@RestController
@RequestMapping("/api/v1/users/me/password")
public class PasswordController {

  private final PasswordUseCase passwords;

  public PasswordController(PasswordUseCase passwords) {
    this.passwords = passwords;
  }

  /** 200 { changedAt, dueAt, due, intervalDays } */
  @GetMapping
  public PasswordStatusResponse status(@CurrentUser long userId) {
    return PasswordStatusResponse.from(passwords.status(userId));
  }

  /** 204. 다른 기기의 세션은 모두 끊기고 지금 세션은 남는다 */
  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void change(
      @CurrentUser long userId,
      @CurrentSession String sessionId,
      @RequestBody(required = false) ChangePasswordRequest request) {
    ChangePasswordRequest r = request == null ? new ChangePasswordRequest(null, null) : request;
    passwords.change(userId, sessionId, r.currentPassword(), r.newPassword());
  }

  /** 200 { changedAt, dueAt, due, intervalDays }. 지금부터 주기만큼 미룬다 */
  @PostMapping("/postpone")
  public PasswordStatusResponse postpone(
      @CurrentUser long userId, @CurrentSession String sessionId) {
    return PasswordStatusResponse.from(passwords.postpone(userId, sessionId));
  }
}
