package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AiLogDetailDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;
import java.util.Map;

/** GET /admin/ai/logs/{id} (07 문서 4장). result는 저장된 결과 JSON 그대로 */
public record AiLogDetailResponse(
    String id,
    String createdAt,
    String userId,
    String userEmail,
    String feature,
    String providerId,
    String model,
    String status,
    int latencyMs,
    Integer inputTokens,
    Integer outputTokens,
    Integer imageBytes,
    String imageSha256,
    String errorCode,
    Map<String, Object> result) {

  public static AiLogDetailResponse from(AiLogDetailDto d) {
    return new AiLogDetailResponse(
        Ids.format(d.id()),
        Times.iso(d.createdAt()),
        Ids.format(d.userId()),
        d.userEmail(),
        d.feature(),
        d.providerId(),
        d.model(),
        d.status(),
        d.latencyMs(),
        d.inputTokens(),
        d.outputTokens(),
        d.imageBytes(),
        d.imageSha256(),
        d.errorCode(),
        d.result());
  }
}
