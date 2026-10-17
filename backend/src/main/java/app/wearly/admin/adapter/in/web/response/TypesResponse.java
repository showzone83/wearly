package app.wearly.admin.adapter.in.web.response;

import app.wearly.ai.application.port.in.dto.ProviderCatalogDto;
import app.wearly.ai.application.port.in.dto.ProviderTypeDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** GET /admin/ai/types (07 문서 4장): 지원 공급자 종류와 options 설명, 서버 설정 */
public record TypesResponse(List<Type> types, boolean baseUrlAllowed, boolean mockMode) {

  public record Type(String type, String description, List<Option> options) {}

  /** options 한 칸. 빈 칸(min·max·values·defaultValue)은 응답에서 뺀다 */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Option(
      String name,
      String kind,
      String description,
      Object defaultValue,
      Number min,
      Number max,
      List<String> values) {}

  public static TypesResponse from(ProviderCatalogDto t) {
    return new TypesResponse(
        t.types().stream().map(TypesResponse::type).toList(), t.baseUrlAllowed(), t.mockMode());
  }

  private static Type type(ProviderTypeDto t) {
    return new Type(
        t.type(),
        t.description(),
        t.options().stream()
            .map(
                o ->
                    new Option(
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
