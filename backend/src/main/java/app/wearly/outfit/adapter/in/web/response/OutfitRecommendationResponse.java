package app.wearly.outfit.adapter.in.web.response;

import app.wearly.outfit.application.port.in.dto.OutfitCandidateDto;
import app.wearly.outfit.application.port.in.dto.OutfitRecommendationDto;
import java.util.List;
import java.util.Map;

/** 09 문서 3장 OutfitRecommendResponse */
public record OutfitRecommendationResponse(
    List<Candidate> candidates, String provider, String model, int remainingToday) {

  /** 후보 하나. items는 칸 → 아이템 id (빈 칸은 키가 없다) */
  public record Candidate(Map<String, String> items, String reason, Scores aiScores) {}

  public record Scores(int color, int occasion, int weather, int taste, int diversity) {}

  public static OutfitRecommendationResponse from(OutfitRecommendationDto d) {
    return new OutfitRecommendationResponse(
        d.candidates().stream().map(OutfitRecommendationResponse::candidate).toList(),
        d.provider(),
        d.model(),
        d.remainingToday());
  }

  private static Candidate candidate(OutfitCandidateDto c) {
    OutfitCandidateDto.ScoresDto s = c.aiScores();
    return new Candidate(
        c.items(),
        c.reason(),
        new Scores(s.color(), s.occasion(), s.weather(), s.taste(), s.diversity()));
  }
}
