package app.wearly.auth.adapter.in.web;

import app.wearly.auth.adapter.in.web.request.LoginRequest;
import app.wearly.auth.adapter.in.web.request.LogoutRequest;
import app.wearly.auth.adapter.in.web.request.MfaTokenRequest;
import app.wearly.auth.adapter.in.web.request.MfaVerifyRequest;
import app.wearly.auth.adapter.in.web.request.RefreshRequest;
import app.wearly.auth.adapter.in.web.request.SignupRequest;
import app.wearly.auth.adapter.in.web.response.AuthResponse;
import app.wearly.auth.adapter.in.web.response.LoginResponse;
import app.wearly.auth.adapter.in.web.response.MfaSetupResponse;
import app.wearly.auth.adapter.in.web.response.MfaVerifyResponse;
import app.wearly.auth.application.port.in.AuthUseCase;
import app.wearly.auth.application.port.in.MfaUseCase;
import app.wearly.auth.application.port.in.dto.MfaVerifyDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthUseCase authService;
  private final MfaUseCase mfa;

  public AuthController(AuthUseCase authService, MfaUseCase mfa) {
    this.authService = authService;
    this.mfa = mfa;
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
    return AuthResponse.from(authService.signup(request.toDto(), ClientHeaders.from(http)));
  }

  /** 토큰, 또는 관리자 사이트에서 2단계 인증이 필요한 관리자면 MFA 대기 토큰 (22 문서) */
  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return LoginResponse.from(authService.login(request.toDto(), ClientHeaders.from(http)));
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(
      @RequestBody(required = false) RefreshRequest request, HttpServletRequest http) {
    return AuthResponse.from(
        authService.refresh(
            request == null ? null : request.refreshToken(), ClientHeaders.from(http)));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @RequestBody(required = false) LogoutRequest request, HttpServletRequest http) {
    authService.logout(request == null ? null : request.refreshToken(), ClientHeaders.from(http));
  }

  /** MFA 대기 토큰으로 TOTP 등록 시작 (22 문서) */
  @PostMapping("/mfa/setup")
  public MfaSetupResponse mfaSetup(@RequestBody(required = false) MfaTokenRequest request) {
    return MfaSetupResponse.from(mfa.setup(request == null ? null : request.mfaToken()));
  }

  /** MFA 대기 토큰 + TOTP 코드(또는 복구 코드) → 정식 토큰 (22 문서) */
  @PostMapping("/mfa/verify")
  public MfaVerifyResponse mfaVerify(
      @RequestBody(required = false) MfaVerifyRequest request, HttpServletRequest http) {
    MfaVerifyDto command = request == null ? new MfaVerifyDto(null, null, null) : request.toDto();
    return MfaVerifyResponse.from(mfa.verify(command, ClientHeaders.from(http)));
  }
}
