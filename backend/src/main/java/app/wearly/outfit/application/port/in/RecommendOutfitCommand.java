package app.wearly.outfit.application.port.in;

import java.util.List;
import java.util.Map;

/**
 * 09 문서 3장 OutfitRecommendRequest (받은 그대로). 검증은 유스케이스가 한다. ID는 문자열이다.
 *
 * @param excludeItemIds 앱이 계산한 제외 목록 (최근 착용 제외 등)
 * @param recent 최근 7일 코디 (착용 기록은 기기에만 있어서 앱이 보낸다)
 * @param count 후보 수 (기본 5, 1~6)
 */
public record RecommendOutfitCommand(
    Context context,
    List<String> excludeItemIds,
    List<Recent> recent,
    List<String> likedItemIds,
    List<String> dislikedItemIds,
    Integer count) {

  /**
   * 추천 조건.
   *
   * @param date YYYY-MM-DD
   * @param fixed 칸 고정 (TOP·BOTTOM·DRESS·OUTER·SHOES·BAG → 아이템 id)
   * @param avoidColors 피하고 싶은 COLOR 코드
   * @param concepts 예정일 컨셉 (STYLE 코드)
   * @param timeOfDay AM · PM · EVENING
   * @param memo 예정일 메모 (200자 이하)
   */
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

  /** 날씨. rainProb 0~100, cond SUNNY · CLOUDY · RAIN */
  public record Weather(Double temp, Double min, Double max, Double rainProb, String cond) {}

  /** 최근 코디 하루 */
  public record Recent(String date, Map<String, String> items) {}
}
