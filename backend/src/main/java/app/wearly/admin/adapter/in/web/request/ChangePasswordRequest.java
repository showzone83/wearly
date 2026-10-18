package app.wearly.admin.adapter.in.web.request;

/** POST /admin/me/password. 값 검사(필수·규칙)는 유스케이스가 한다 (가입과 같은 비밀번호 규칙) */
public record ChangePasswordRequest(String currentPassword, String newPassword) {

  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
  }
}
