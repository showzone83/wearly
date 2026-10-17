package app.wearly.auth.application.port.out;

/** 비밀번호 해시 (BCrypt) */
public interface PasswordHasher {

  String encode(String rawPassword);

  boolean matches(String rawPassword, String hash);
}
