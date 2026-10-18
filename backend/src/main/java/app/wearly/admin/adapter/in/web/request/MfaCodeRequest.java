package app.wearly.admin.adapter.in.web.request;

/**
 * POST /admin/me/mfa/enable {code}, POST /admin/me/mfa/reset {currentPassword, code | recoveryCode}
 */
public record MfaCodeRequest(String currentPassword, String code, String recoveryCode) {

  @Override
  public String toString() {
    return "MfaCodeRequest[currentPassword=***, code=***, recoveryCode=***]";
  }
}
