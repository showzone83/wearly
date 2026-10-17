package app.wearly.auth.adapter.out.security;

import app.wearly.auth.application.port.out.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** PasswordHasher 구현 (Spring Security PasswordEncoder = BCrypt, SecurityConfig) */
@Component
class BcryptPasswordHasher implements PasswordHasher {

  private final PasswordEncoder encoder;

  BcryptPasswordHasher(PasswordEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String encode(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String hash) {
    return encoder.matches(rawPassword, hash);
  }
}
