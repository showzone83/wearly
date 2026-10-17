package app.wearly.admin.adapter.in.web.response;

import app.wearly.admin.application.port.in.dto.AiLogDto;
import app.wearly.common.web.Ids;
import app.wearly.common.web.Times;

/** GET /admin/ai/logs 항목 (07 문서 4장). ID는 문자열, 시각은 ISO-8601 UTC */
public record AiLogResponse(
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
    String resultSummary,
    String errorCode) {

  public static AiLogResponse from(AiLogDto d) {
    return new AiLogResponse(
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
        d.resultSummary(),
        d.errorCode());
  }
}
