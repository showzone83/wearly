package app.wearly.outfit.application.port.in;

import app.wearly.outfit.domain.OutfitCandidate;
import java.util.List;

/**
 * 09 문서 3장 OutfitRecommendResponse.
 *
 * @param provider 응답한 공급자 ID (목업이면 "mock")
 * @param remainingToday 오늘 남은 AI 코디 추천 횟수
 */
public record OutfitRecommendation(
    List<OutfitCandidate> candidates, String provider, String model, int remainingToday) {}
