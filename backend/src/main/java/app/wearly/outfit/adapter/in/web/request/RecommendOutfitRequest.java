package app.wearly.outfit.adapter.in.web.request;

import app.wearly.outfit.application.port.in.dto.RecommendOutfitDto;
import java.util.List;
import java.util.Map;

/**
 * POST /outfits/recommendations 본문 (09 문서 3장 OutfitRecommendRequest). JSON 모양만 정하고 검증은 유스케이스가 한다.
 * ID는 문자열이다.
 */
public record RecommendOutfitRequest(
    Context context,
    List<String> excludeItemIds,
    List<Recent> recent,
    List<String> likedItemIds,
    List<String> dislikedItemIds,
    Integer count) {

  public record Context(
      String date,
      String occasion,
      String mood,
      String formality,
      Weather weather,
      String anchorItemId,
      Map<String, String> fixed,
      List<String> avoidColors,
      List<String> concepts,
      String timeOfDay,
      String memo) {}

  public record Weather(Double temp, Double min, Double max, Double rainProb, String cond) {}

  public record Recent(String date, Map<String, String> items) {}

  public RecommendOutfitDto toDto() {
    return new RecommendOutfitDto(
        context == null ? null : context(context),
        excludeItemIds,
        recent == null ? null : recent.stream().map(RecommendOutfitRequest::recent).toList(),
        likedItemIds,
        dislikedItemIds,
        count);
  }

  private static RecommendOutfitDto.Context context(Context c) {
    Weather w = c.weather();
    return new RecommendOutfitDto.Context(
        c.date(),
        c.occasion(),
        c.mood(),
        c.formality(),
        w == null
            ? null
            : new RecommendOutfitDto.Weather(w.temp(), w.min(), w.max(), w.rainProb(), w.cond()),
        c.anchorItemId(),
        c.fixed(),
        c.avoidColors(),
        c.concepts(),
        c.timeOfDay(),
        c.memo());
  }

  private static RecommendOutfitDto.Recent recent(Recent r) {
    return r == null ? null : new RecommendOutfitDto.Recent(r.date(), r.items());
  }
}
