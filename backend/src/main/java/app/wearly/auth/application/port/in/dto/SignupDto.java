package app.wearly.auth.application.port.in.dto;

/**
 * 이메일 가입 입력.
 *
 * @param inviteCode 친구의 초대 코드 (선택, doc/25). null이면 없음
 */
public record SignupDto(String email, String password, String name, String inviteCode) {

  /** 초대 코드 없이 */
  public SignupDto(String email, String password, String name) {
    this(email, password, name, null);
  }
}
