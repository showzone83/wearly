package app.wearly.wardrobe.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 아이템 등록·수정 값 (05 문서 6장 ItemInput). wornCount·lastWornAt·purchasedAt은 선택. 종류·세부 종류·색·패턴·계절·스타일·보유
 * 상태는 코드 문자열이고, 필수·길이·범위 불변식은 ItemRules(도메인)가, 코드 테이블 검증은 ItemCodeValidator가 한다. 웹 요청의 Bean
 * Validation은 빠른 실패용이다.
 *
 * @param colors 색 1~3개, 첫 번째가 주 색상 (이슈 #4). 없으면 null
 * @param legacyColor colors가 예전 입력 color(하나)에서 왔는지(PATCH에서 color만 보낸 경우 포함). 그러면 주 색상의 오류(필수·코드)를
 *     예전처럼 color 필드로 알린다
 * @param purchasedAt 구입일. null이면 모름 → 등록일로 저장하고 추정 표시
 */
public record ItemInput(
    String name,
    String category,
    String subType,
    List<String> colors,
    boolean legacyColor,
    String pattern,
    List<String> seasons,
    List<String> styles,
    Integer formality,
    String status,
    String brand,
    Integer wornCount,
    LocalDate lastWornAt,
    LocalDate purchasedAt) {

  /** 웹 입력의 colors·예전 color를 하나로: colors가 오면 colors(둘 다 오면 colors 우선), 아니면 color 하나, 둘 다 없으면 null */
  public static List<String> colorsOf(String color, List<String> colors) {
    if (colors != null) {
      return colors;
    }
    return color == null ? null : List.of(color);
  }

  /** colorsOf가 예전 color에서 만든 값인지 */
  public static boolean isLegacy(String color, List<String> colors) {
    return colors == null && color != null;
  }

  /** 주 색상 (없으면 null) */
  public String color() {
    return colors == null || colors.isEmpty() ? null : colors.get(0);
  }
}
