package app.wearly.ai.adapter.in.web.response;

import app.wearly.ai.application.port.in.dto.AiSuggestionDto;
import java.util.List;

/**
 * AnalyzeResponse.suggestion (05 문서 6장). 관리자 시험 분석 응답도 같은 모양을 쓴다. colors는 1~3개, 첫 번째가 color (이슈 #4)
 */
public record SuggestionResponse(
    String name,
    String category,
    String subType,
    String color,
    List<String> colors,
    String pattern,
    List<String> seasons,
    List<String> styles,
    int formality,
    double confidence) {

  public static SuggestionResponse from(AiSuggestionDto s) {
    return s == null
        ? null
        : new SuggestionResponse(
            s.name(),
            s.category(),
            s.subType(),
            s.color(),
            s.colors(),
            s.pattern(),
            s.seasons(),
            s.styles(),
            s.formality(),
            s.confidence());
  }
}
