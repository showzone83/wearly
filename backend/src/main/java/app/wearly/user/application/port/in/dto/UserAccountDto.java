package app.wearly.user.application.port.in.dto;

import app.wearly.user.domain.UserRole;
import app.wearly.user.domain.UserStatus;
import java.time.Instant;

/** 다른 컨텍스트(인증·관리자)와 웹 어댑터에 내보내는 회원 정보. 상태·역할은 도메인 값의 이름 */
public record UserAccountDto(
    long id, String email, String name, String role, String status, Instant createdAt) {

  public static final String ACTIVE = UserStatus.ACTIVE.name();
  public static final String SUSPENDED = UserStatus.SUSPENDED.name();
  public static final String WITHDRAWN = UserStatus.WITHDRAWN.name();
  public static final String ROLE_USER = UserRole.USER.name();
  public static final String ROLE_ADMIN = UserRole.ADMIN.name();

  public boolean isActive() {
    return ACTIVE.equals(status);
  }

  /** 탈퇴 예정 (doc/27) */
  public boolean isWithdrawn() {
    return WITHDRAWN.equals(status);
  }

  public boolean isAdmin() {
    return ROLE_ADMIN.equals(role);
  }
}
