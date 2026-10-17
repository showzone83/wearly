package app.wearly.ai.domain;

/**
 * AI 실행 설정 (환경변수에서 읽어 설정이 만든다).
 *
 * @param mockMode     WEARLY_AI_PROVIDER=mock
 * @param dailyLimit   회원별 하루 사진 분석 횟수
 * @param allowBaseUrl 공급자 options.baseUrl 허용 여부
 */
public record AiSettings(boolean mockMode, int dailyLimit, boolean allowBaseUrl) {
}
