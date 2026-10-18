package app.wearly.auth.adapter.in.web.request;

import app.wearly.auth.application.port.in.dto.LoginDto;
import jakarta.validation.constraints.NotBlank;

/** POST /auth/login (05 문서 6장) */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

  public LoginDto toDto() {
    return new LoginDto(email, password);
  }
}
