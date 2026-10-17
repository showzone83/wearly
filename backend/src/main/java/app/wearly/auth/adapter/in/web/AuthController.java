package app.wearly.auth.adapter.in.web;

import app.wearly.auth.adapter.in.web.AuthDtos.AuthResponse;
import app.wearly.auth.adapter.in.web.AuthDtos.LoginRequest;
import app.wearly.auth.adapter.in.web.AuthDtos.LogoutRequest;
import app.wearly.auth.adapter.in.web.AuthDtos.RefreshRequest;
import app.wearly.auth.adapter.in.web.AuthDtos.SignupRequest;
import app.wearly.auth.application.port.in.AuthUseCase;
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
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return AuthResponse.from(authService.signup(request.toCommand()));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return AuthResponse.from(authService.login(request.toCommand()));
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@RequestBody(required = false) RefreshRequest request) {
    return AuthResponse.from(authService.refresh(request == null ? null : request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestBody(required = false) LogoutRequest request) {
    authService.logout(request == null ? null : request.refreshToken());
  }
}
