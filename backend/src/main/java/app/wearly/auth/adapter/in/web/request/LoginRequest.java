package app.wearly.auth.adapter.in.web.request;

import app.wearly.auth.application.port.in.dto.LoginDto;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /auth/login (05 문서 6장).
 *
 * @param cancelWithdrawal true면 탈퇴 예정 계정의 탈퇴를 취소하고 로그인한다 (doc/27, 없으면 false)
 */
public record LoginRequest(
    @NotBlank String email, @NotBlank String password, Boolean cancelWithdrawal) {

  public LoginDto toDto() {
    return new LoginDto(email, password, Boolean.TRUE.equals(cancelWithdrawal));
  }
}
