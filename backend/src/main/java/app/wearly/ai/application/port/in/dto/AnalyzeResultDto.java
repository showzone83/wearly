package app.wearly.ai.application.port.in.dto;

/**
 * 사진 분석 결과 (유스케이스 출력, 05 문서 6장 AnalyzeResponse의 값).
 *
 * @param provider 응답한 공급자 ID (목업이면 "mock")
 * @param remainingToday 오늘 남은 사진 분석 횟수
 */
public record AnalyzeResultDto(
    AiSuggestionDto suggestion, String provider, String model, int remainingToday) {}
