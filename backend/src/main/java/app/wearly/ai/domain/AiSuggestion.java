package app.wearly.ai.domain;

import java.util.List;

/**
 * AnalyzeResponse.suggestion (05 문서 6장). 속성 값은 활성 코드 문자열 (06 문서). colors는 1~3개, 첫 번째가 color(주 색상)
 * (이슈 #4)
 */
public record AiSuggestion(
    String name,
    String category,
    String subType,
    String color,
    List<String> colors,
    String pattern,
    List<String> seasons,
    List<String> styles,
    int formality,
    double confidence) {}
