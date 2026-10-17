package app.wearly.admin.adapter.in.web.response;

import app.wearly.ai.application.port.in.dto.AiSuggestionDto;
import app.wearly.ai.application.port.in.dto.ProviderTestResultDto;
import java.util.List;

/** POST /admin/ai/providers/{id}/test (07 문서 4장). 실패해도 HTTP 200, ok=false */
public record ProviderTestResultResponse(
    boolean ok,
    Suggestion suggestion,
    String errorCode,
    String errorMessage,
    int latencyMs,
    Long inputTokens,
    Long outputTokens,
    String model) {

  /** 사진 분석 응답의 suggestion과 같은 모양 */
  public record Suggestion(
      String name,
      String category,
      String subType,
      String color,
      String pattern,
      List<String> seasons,
      List<String> styles,
      int formality,
      double confidence) {}

  public static ProviderTestResultResponse from(ProviderTestResultDto r) {
    AiSuggestionDto s = r.suggestion();
    return new ProviderTestResultResponse(
        r.ok(),
        s == null
            ? null
            : new Suggestion(
                s.name(),
                s.category(),
                s.subType(),
                s.color(),
                s.pattern(),
                s.seasons(),
                s.styles(),
                s.formality(),
                s.confidence()),
        r.errorCode(),
        r.errorMessage(),
        r.latencyMs(),
        r.inputTokens(),
        r.outputTokens(),
        r.model());
  }
}
