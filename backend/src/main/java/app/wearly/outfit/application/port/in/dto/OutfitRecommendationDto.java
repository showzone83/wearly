package app.wearly.outfit.application.port.in.dto;

import java.util.List;

/**
 * 코디 추천 결과 (유스케이스 출력, 09 문서 3장).
 *
 * @param provider 응답한 공급자 ID (목업이면 "mock")
 * @param remainingToday 오늘 남은 AI 코디 추천 횟수
 */
public record OutfitRecommendationDto(
    List<OutfitCandidateDto> candidates, String provider, String model, int remainingToday) {}
