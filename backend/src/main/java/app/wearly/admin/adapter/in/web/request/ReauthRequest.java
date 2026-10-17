package app.wearly.admin.adapter.in.web.request;

/** DELETE /admin/ai/providers/{id}/api-key: 관리자 비밀번호 재확인 */
public record ReauthRequest(String currentPassword) {

  @Override
  public String toString() {
    return "ReauthRequest[currentPassword=***]";
  }
}
