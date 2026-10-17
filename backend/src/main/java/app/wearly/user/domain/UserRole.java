package app.wearly.user.domain;

import java.util.Optional;

/** 회원 역할 (tb_user.role, V3 CHECK와 같다). 관리자는 가입 API로 만들 수 없다 */
public enum UserRole {
  USER,
  ADMIN;

  static final String MESSAGE = "USER 또는 ADMIN이어야 해요.";

  /** 정확한 이름만 받는다 (대소문자·공백 그대로) */
  public static Optional<UserRole> parse(String raw) {
    for (UserRole r : values()) {
      if (r.name().equals(raw)) {
        return Optional.of(r);
      }
    }
    return Optional.empty();
  }
}
