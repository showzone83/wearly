package app.wearly.ai.application.port.in.dto;

import app.wearly.ai.domain.AiSuggestion;
import java.util.List;

/** 사진 분석 제안 (유스케이스 출력). 속성 값은 활성 코드 문자열 (06 문서). colors는 1~3개, 첫 번째가 color (이슈 #4) */
public record AiSuggestionDto(
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

  public static AiSuggestionDto from(AiSuggestion s) {
    return s == null
        ? null
        : new AiSuggestionDto(
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
