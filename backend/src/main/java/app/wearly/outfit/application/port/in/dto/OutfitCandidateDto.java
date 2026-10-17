package app.wearly.outfit.application.port.in.dto;

import app.wearly.outfit.domain.AiScores;
import app.wearly.outfit.domain.OutfitCandidate;
import java.util.Map;

/**
 * 검증을 통과한 코디 후보 (유스케이스 출력).
 *
 * @param items 칸 → 아이템 id (칸 순서, 빈 칸은 키가 없다)
 * @param reason 한국어 한두 문장 (80자 이하)
 */
public record OutfitCandidateDto(Map<String, String> items, String reason, ScoresDto aiScores) {

  /** AI가 매긴 5개 기준 0~100 예상 점수 */
  public record ScoresDto(int color, int occasion, int weather, int taste, int diversity) {}

  public static OutfitCandidateDto from(OutfitCandidate c) {
    AiScores s = c.aiScores();
    return new OutfitCandidateDto(
        c.items(),
        c.reason(),
        new ScoresDto(s.color(), s.occasion(), s.weather(), s.taste(), s.diversity()));
  }
}
