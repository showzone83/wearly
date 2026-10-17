package app.wearly.auth.adapter.in.web.request;

import app.wearly.auth.application.port.in.dto.SignupDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/signup (05 문서 6장) */
public record SignupRequest(
    @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        @Size(max = 255, message = "이메일이 너무 길어요.")
        String email,
    @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$",
            message = "비밀번호는 8~64자, 영문과 숫자를 모두 포함해야 해요.")
        String password,
    @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 20, message = "이름은 20자까지 쓸 수 있어요.")
        String name) {

  public SignupDto toDto() {
    return new SignupDto(email, password, name);
  }
}
