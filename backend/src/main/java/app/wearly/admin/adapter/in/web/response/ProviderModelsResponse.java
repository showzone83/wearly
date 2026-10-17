package app.wearly.admin.adapter.in.web.response;

import app.wearly.ai.application.port.in.dto.AiModelListDto;
import app.wearly.common.web.Times;
import java.util.List;

/**
 * GET /admin/ai/providers/{providerId}/models (07 문서 4장): 모델 드롭다운 목록.
 *
 * @param source LIVE(제공사에서 받은 목록) 또는 CATALOG(내장 목록)
 * @param fetchedAt LIVE면 제공사에서 받은 시각(캐시면 처음 받은 시각), CATALOG면 응답 시각
 * @param error CATALOG일 때 이유 { code: NO_API_KEY | MODEL_LIST_FAILED, message }, LIVE면 null
 */
public record ProviderModelsResponse(
    String source, String fetchedAt, List<Model> models, ErrorInfo error) {

  /** createdAt은 제공사가 알려 주지 않으면 null (Gemini, 내장 목록) */
  public record Model(String id, String displayName, String createdAt, boolean recommended) {}

  public record ErrorInfo(String code, String message) {}

  public static ProviderModelsResponse from(AiModelListDto d) {
    return new ProviderModelsResponse(
        d.source(),
        Times.iso(d.fetchedAt()),
        d.models().stream()
            .map(m -> new Model(m.id(), m.displayName(), Times.iso(m.createdAt()), m.recommended()))
            .toList(),
        d.error() == null ? null : new ErrorInfo(d.error().code(), d.error().message()));
  }
}
