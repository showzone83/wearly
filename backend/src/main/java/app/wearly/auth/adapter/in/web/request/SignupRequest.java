package app.wearly.auth.adapter.in.web.request;

import app.wearly.auth.application.port.in.dto.SignupDto;
import app.wearly.auth.domain.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/signup (05 문서 6장) */
public record SignupRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Pattern(regexp = PasswordPolicy.PATTERN) String password,
    @NotBlank @Size(max = 20) String name) {

  public SignupDto toDto() {
    return new SignupDto(email, password, name);
  }
}
