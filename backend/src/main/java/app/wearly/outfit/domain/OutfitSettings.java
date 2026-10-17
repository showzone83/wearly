package app.wearly.outfit.domain;

/**
 * 코디 추천 설정 (환경변수에서 읽어 설정이 만든다).
 *
 * @param dailyLimit 회원별 하루 AI 코디 추천 횟수 (WEARLY_AI_OUTFIT_DAILY_LIMIT, 성공만 센다)
 */
public record OutfitSettings(int dailyLimit) {}
