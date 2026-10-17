package app.wearly.admin.adapter.in.web.request;

/** PUT /admin/ai/providers/{id}/api-key (07 문서 6장). 키는 쓰기 전용 — toString에도 남기지 않는다 */
public record ApiKeyRequest(String apiKey, String currentPassword, Boolean verify) {

  public static final ApiKeyRequest EMPTY = new ApiKeyRequest(null, null, null);

  @Override
  public String toString() {
    return "ApiKeyRequest[apiKey=***, currentPassword=***, verify=" + verify + "]";
  }
}
