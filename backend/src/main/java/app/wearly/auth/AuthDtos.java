package app.wearly.auth;

import app.wearly.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 계약 6장 인증 요청·응답 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "이메일 형식이 아니에요.")
            @Size(max = 255, message = "이메일이 너무 길어요.")
            String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$",
                    message = "비밀번호는 8~64자, 영문과 숫자를 모두 포함해야 해요.")
            String password,
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 20, message = "이름은 20자까지 쓸 수 있어요.")
            String name) {
    }

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해 주세요.") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "refreshToken이 필요해요.") String refreshToken) {
    }

    /** 로그아웃은 토큰이 없거나 틀려도 204라서 검증하지 않는다. */
    public record LogoutRequest(String refreshToken) {
    }

    public record AuthResponse(
            String accessToken,
            long accessTokenExpiresIn,
            String refreshToken,
            long refreshTokenExpiresIn,
            UserDto user) {
    }
}
