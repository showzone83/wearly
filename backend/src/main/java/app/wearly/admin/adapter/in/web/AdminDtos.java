package app.wearly.admin.adapter.in.web;

import app.wearly.admin.application.port.in.AdminAiProviderUseCase;
import app.wearly.ai.application.port.in.dto.ProviderTypeDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 관리자 API 요청·응답 DTO (07 문서 4장). 그 밖의 응답은 port.in 뷰(AdminViews)를 그대로 쓴다 */
public final class AdminDtos {

  private AdminDtos() {}

  /** GET /admin/ai/types의 options 한 칸. 빈 칸(min·max·values·defaultValue)은 응답에서 뺀다 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OptionDto(
      String name,
      String kind,
      String description,
      Object defaultValue,
      Number min,
      Number max,
      List<String> values) {}

  public record TypeDto(String type, String description, List<OptionDto> options) {}

  public record TypesResponse(List<TypeDto> types, boolean baseUrlAllowed, boolean mockMode) {

    static TypesResponse from(AdminAiProviderUseCase.Types t) {
      return new TypesResponse(
          t.types().stream().map(TypesResponse::type).toList(), t.baseUrlAllowed(), t.mockMode());
    }

    private static TypeDto type(ProviderTypeDto t) {
      return new TypeDto(
          t.type(),
          t.description(),
          t.options().stream()
              .map(
                  o ->
                      new OptionDto(
                          o.name(),
                          o.kind(),
                          o.description(),
                          o.defaultValue(),
                          o.min(),
                          o.max(),
                          o.values()))
              .toList());
    }
  }

  /** PUT /admin/ai/providers/{id}/api-key. 키는 쓰기 전용 — toString에도 남기지 않는다 */
  public record ApiKeyRequest(String apiKey, String currentPassword, Boolean verify) {

    @Override
    public String toString() {
      return "ApiKeyRequest[apiKey=***, currentPassword=***, verify=" + verify + "]";
    }
  }

  /** DELETE /admin/ai/providers/{id}/api-key */
  public record ReauthRequest(String currentPassword) {

    @Override
    public String toString() {
      return "ReauthRequest[currentPassword=***]";
    }
  }

  /** PUT /admin/codes/{group}/order */
  public record OrderRequest(List<String> codes) {}
}
