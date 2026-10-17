package app.wearly.auth.adapter.in.web;

import app.wearly.auth.adapter.in.web.request.LoginRequest;
import app.wearly.auth.adapter.in.web.request.LogoutRequest;
import app.wearly.auth.adapter.in.web.request.RefreshRequest;
import app.wearly.auth.adapter.in.web.request.SignupRequest;
import app.wearly.auth.adapter.in.web.response.AuthResponse;
import app.wearly.auth.application.port.in.AuthUseCase;
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

  public AuthController(AuthUseCase authService) {
    this.authService = authService;
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
    return AuthResponse.from(authService.signup(request.toDto(), ClientHeaders.from(http)));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return AuthResponse.from(authService.login(request.toDto(), ClientHeaders.from(http)));
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
}
