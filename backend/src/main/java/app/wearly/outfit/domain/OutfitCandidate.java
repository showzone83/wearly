package app.wearly.outfit.domain;

import java.util.Map;

/**
 * 검증을 통과한 코디 후보 (09 문서 3장 OutfitRecommendResponse.candidates 항목).
 *
 * @param items 칸 → 아이템 id (칸 순서, 빈 칸은 키가 없다)
 * @param reason 한국어 한두 문장 (80자 이하)
 */
public record OutfitCandidate(Map<String, String> items, String reason, AiScores aiScores) {}
