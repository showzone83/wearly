package app.wearly.auth.application.port.in.dto;

/**
 * 이메일 로그인 입력.
 *
 * @param cancelWithdrawal 탈퇴 예정 계정이면 탈퇴를 취소하고 로그인한다 (doc/27). false면 403 ACCOUNT_WITHDRAWN
 */
public record LoginDto(String email, String password, boolean cancelWithdrawal) {

  public LoginDto(String email, String password) {
    this(email, password, false);
  }
}
