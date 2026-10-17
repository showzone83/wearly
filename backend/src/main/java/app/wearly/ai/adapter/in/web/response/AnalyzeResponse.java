package app.wearly.ai.adapter.in.web.response;

import app.wearly.ai.application.port.in.dto.AnalyzeResultDto;

/** POST /wardrobe/items/analyze 응답 (05 문서 6장 AnalyzeResponse) */
public record AnalyzeResponse(
    SuggestionResponse suggestion, String provider, String model, int remainingToday) {

  public static AnalyzeResponse from(AnalyzeResultDto r) {
    return new AnalyzeResponse(
        SuggestionResponse.from(r.suggestion()), r.provider(), r.model(), r.remainingToday());
  }
}
