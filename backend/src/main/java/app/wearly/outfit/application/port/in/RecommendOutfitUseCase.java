package app.wearly.outfit.application.port.in;

/**
 * AI 코디 추천 (09 문서 3장 POST /outfits/recommendations): 요청 검증 → 옷장·프로필·코드 읽기 → 옷 부족 400 → 일일 한도 429 →
 * 공급자를 우선순위 순으로 시도하며 후보 검증 (남은 후보 0개면 다음 공급자) → 모두 실패 502.
 */
public interface RecommendOutfitUseCase {

  OutfitRecommendation recommend(long userId, RecommendOutfitCommand command);
}
