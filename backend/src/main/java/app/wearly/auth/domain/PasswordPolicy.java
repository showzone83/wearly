package app.wearly.auth.domain;

/** 비밀번호 규칙: 8~64자, 영문과 숫자를 하나 이상 (가입·비밀번호 변경이 같이 쓴다) */
public final class PasswordPolicy {

  public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$";

  private PasswordPolicy() {}

  public static boolean isValid(String password) {
    return password != null && password.matches(PATTERN);
  }
}
