package app.wearly.outfit.domain;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검증을 마친 코디 추천 요청 (09 문서 3장 OutfitRecommendRequest).
 *
 * @param weather 없으면 null (날씨 기준을 빼고 판단)
 * @param anchorItemId 꼭 넣을 중심 아이템. 없으면 null
 * @param fixed 칸 고정 (칸 → 아이템 id, 칸 순서)
 * @param excludeItemIds 앱이 계산한 제외 목록 (최근 착용 등)
 * @param recent 최근 7일 코디 (날짜 최신순)
 * @param count 후보 수 (1~6)
 */
public record OutfitRequest(
    LocalDate date,
    String occasion,
    String mood,
    String formality,
    Weather weather,
    String anchorItemId,
    Map<String, String> fixed,
    List<String> avoidColors,
    List<String> concepts,
    String timeOfDay,
    String memo,
    Set<String> excludeItemIds,
    List<RecentOutfit> recent,
    Set<String> likedItemIds,
    Set<String> dislikedItemIds,
    int count) {

  public static final int DEFAULT_COUNT = 5;
  public static final int MAX_COUNT = 6;

  /** 고정·중심 아이템 id (요청 순서: 고정 칸 순서 → 중심) */
  public Set<String> pinnedIds() {
    Set<String> out = new LinkedHashSet<>(fixed.values());
    if (anchorItemId != null) {
      out.add(anchorItemId);
    }
    return out;
  }

  /**
   * 날씨 (앱 Weather). 기온 판단은 앱 규칙 엔진(recommend.ts)과 같다.
   *
   * @param rainProb 강수 확률 0~100
   * @param cond SUNNY · CLOUDY · RAIN
   */
  public record Weather(double temp, double min, double max, int rainProb, String cond) {

    /** 아우터가 필요한 날: 17°C 아래이거나 일교차 10°C 이상에 최저 16°C 아래 */
    public boolean wantsOuter() {
      return temp < 17 || (max - min >= 10 && min < 16);
    }

    /** 기온에 맞는 계절 (SEASON 코드) */
    public List<String> seasons() {
      if (temp >= 24) {
        return List.of("SUMMER");
      }
      if (temp >= 17) {
        return List.of("SPRING", "FALL", "SUMMER");
      }
      if (temp >= 9) {
        return List.of("SPRING", "FALL");
      }
      if (temp >= 4) {
        return List.of("FALL", "WINTER");
      }
      return List.of("WINTER");
    }

    /** 몸에 걸치는 옷(상의·하의·원피스·아우터) 보온(warmth) 합의 목표 */
    public int neededWarmth() {
      if (temp < 5) {
        return 5;
      }
      if (temp < 10) {
        return 4;
      }
      if (temp < 15) {
        return 3;
      }
      return temp < 20 ? 2 : 1;
    }

    /** 비 예보: 강수 확률 60% 이상 (젖어도 티가 덜 나는 어두운 신발) */
    public boolean rainy() {
      return rainProb >= 60;
    }
  }

  /** 최근 코디 하루 (칸 → 아이템 id) */
  public record RecentOutfit(LocalDate date, Map<String, String> items) {}
}
