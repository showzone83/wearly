package app.wearly.outfit.domain;

import java.util.List;

/**
 * 프롬프트에 주는 사용자 취향 (프로필의 드레스 코드·선호 스타일·선호 색, 코드 문자열) + 나의 정보 (연령대·체형·키·핏 선호, 모두 선택이라 null일 수 있다).
 */
public record OutfitTaste(
    List<String> dressCodes,
    List<String> moods,
    List<String> colors,
    String ageRange,
    String bodyType,
    Integer heightCm,
    String fitPreference) {

  /** 나의 정보 없이 */
  public OutfitTaste(List<String> dressCodes, List<String> moods, List<String> colors) {
    this(dressCodes, moods, colors, null, null, null, null);
  }
}
