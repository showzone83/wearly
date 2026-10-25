package app.wearly.user.domain;

import app.wearly.common.domain.DomainValidationException;
import app.wearly.common.domain.FieldError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원 (순수 도메인 모델). 이름은 1~20자(공백만은 안 됨), 상태·역할은 정해진 값만 (H2). 규칙을 어기면 DomainValidationException. 저장은
 * 영속성 어댑터가 UserEntity로 바꿔 한다.
 */
public class User {

  public static final int NAME_MAX = 20;

  /** 관리자가 상태 바꾸기로 넣을 수 있는 값 */
  static final List<String> ACCESS_STATUSES =
      List.of(UserStatus.ACTIVE.name(), UserStatus.SUSPENDED.name());

  private final Long id;
  private final String email;
  private String name;
  private UserStatus status;
  private UserRole role;
  private final Instant createdAt;

  public User(
      Long id, String email, String name, UserStatus status, UserRole role, Instant createdAt) {
    this.id = id;
    this.email = email;
    this.name = name;
    this.status = status;
    this.role = role;
    this.createdAt = createdAt;
  }

  /** 아직 저장하지 않은 새 회원 (상태 ACTIVE). 이름은 받은 그대로 저장한다 (앞뒤 공백 정리는 부르는 쪽) */
  public static User newUser(String email, String name, UserRole role) {
    checkName(name);
    return new User(null, email, name, UserStatus.ACTIVE, role, null);
  }

  /**
   * 문자열 상태·역할을 바꾼다 (관리자). 허용 값이 아니면 status·role 필드 오류를 함께 모아 DomainValidationException. 탈퇴
   * 예정(WITHDRAWN)은 넣을 수도, 탈퇴 예정인 회원을 바꿀 수도 없다 — 탈퇴 신청·취소로만 바뀐다 (doc/27)
   */
  public void changeAccess(String status, String role) {
    List<FieldError> errors = new ArrayList<>();
    UserStatus s = UserStatus.parse(status).orElse(null);
    if (s == null || s == UserStatus.WITHDRAWN || this.status == UserStatus.WITHDRAWN) {
      errors.add(FieldError.notAllowed("status", ACCESS_STATUSES));
    }
    UserRole r = UserRole.parse(role).orElse(null);
    if (r == null) {
      errors.add(FieldError.notAllowed("role", names(UserRole.values())));
    }
    DomainValidationException.throwIfAny(errors);
    this.status = s;
    this.role = r;
  }

  public void rename(String name) {
    checkName(name);
    this.name = name;
  }

  /** 탈퇴를 신청한다 (탈퇴 예정). 이미 탈퇴 예정이면 false */
  public boolean withdraw() {
    if (status == UserStatus.WITHDRAWN) {
      return false;
    }
    this.status = UserStatus.WITHDRAWN;
    return true;
  }

  /** 탈퇴를 취소한다 (다시 사용 중). 탈퇴 예정이 아니면 false */
  public boolean cancelWithdrawal() {
    if (status != UserStatus.WITHDRAWN) {
      return false;
    }
    this.status = UserStatus.ACTIVE;
    return true;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean isWithdrawn() {
    return status == UserStatus.WITHDRAWN;
  }

  public boolean isAdmin() {
    return role == UserRole.ADMIN;
  }

  private static void checkName(String name) {
    if (name == null || name.isBlank()) {
      throw new DomainValidationException(List.of(FieldError.required("name")));
    }
    if (name.length() > NAME_MAX) {
      throw new DomainValidationException(List.of(FieldError.tooLong("name", NAME_MAX)));
    }
  }

  private static List<String> names(Enum<?>[] values) {
    return java.util.Arrays.stream(values).map(Enum::name).toList();
  }

  public Long id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String name() {
    return name;
  }

  public UserStatus status() {
    return status;
  }

  public UserRole role() {
    return role;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
