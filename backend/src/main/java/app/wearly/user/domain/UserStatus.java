package app.wearly.user.domain;

import java.util.Optional;

/** 회원 상태 (tb_user.status, V1 CHECK와 같다). 정지되면 로그인·토큰 갱신이 막힌다 */
public enum UserStatus {
  ACTIVE,
  SUSPENDED;

  /** 정확한 이름만 받는다 (대소문자·공백 그대로) */
  public static Optional<UserStatus> parse(String raw) {
    for (UserStatus s : values()) {
      if (s.name().equals(raw)) {
        return Optional.of(s);
      }
    }
    return Optional.empty();
  }
}
