package app.wearly.user.domain;

import java.util.Optional;

/**
 * 회원 상태 (tb_user.status, V1·V30 CHECK와 같다). 정지·탈퇴 예정이면 로그인·토큰 갱신이 막힌다. 탈퇴 예정(WITHDRAWN)은 회원이 탈퇴를
 * 신청해 유예 기간을 기다리는 상태로, 관리자가 상태 바꾸기로 넣거나 뺄 수 없다 (doc/27)
 */
public enum UserStatus {
  ACTIVE,
  SUSPENDED,
  WITHDRAWN;

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
