package app.wearly.ai;

import java.util.List;

/** AnalyzeResponse.suggestion (05 문서 6장). 속성 값은 활성 코드 문자열 (06 문서) */
public record AiSuggestion(
        String name,
        String category,
        String subType,
        String color,
        String pattern,
        List<String> seasons,
        List<String> styles,
        int formality,
        double confidence) {
}
