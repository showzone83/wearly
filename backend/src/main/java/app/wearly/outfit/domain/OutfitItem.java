package app.wearly.outfit.domain;

import app.wearly.sharedkernel.code.CodeEntry;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 코디 추천에 쓰는 옷장 아이템 한 벌. 옷장 값에 코드 속성(칸·보온·볼륨·색의 뉴트럴·밝기·추천 가능 상태)을 붙인 모양이다.
 *
 * @param slot CATEGORY attrs.slot. null이면 코디에 넣지 않는다 (액세서리 등)
 * @param color 주 색상. 뉴트럴·밝기·피하고 싶은 색은 주 색상으로 본다
 * @param subColors 보조 색 0~2개 (이슈 #4). 색 짝·반복 판단에 주 색상보다 약하게 쓴다
 * @param recommendable ITEM_STATUS attrs.recommendable (세탁·수선 중이면 false)
 */
public record OutfitItem(
    String id,
    String name,
    String slot,
    String category,
    String subType,
    String color,
    List<String> subColors,
    String pattern,
    List<String> seasons,
    List<String> styles,
    int formality,
    int warmth,
    int volume,
    boolean neutral,
    double light,
    int wornCount,
    LocalDate lastWornAt,
    String status,
    boolean recommendable) {

  /** 옷장 값 + 코드 스냅샷 */
  public static OutfitItem of(
      String id,
      String name,
      String category,
      String subType,
      String color,
      List<String> subColors,
      String pattern,
      List<String> seasons,
      List<String> styles,
      int formality,
      String status,
      int wornCount,
      LocalDate lastWornAt,
      CodeSnapshot codes) {
    Map<String, Object> categoryAttrs = attrs(codes, CodeGroups.CATEGORY, category);
    Map<String, Object> subTypeAttrs = attrs(codes, CodeGroups.SUB_TYPE, subType);
    Map<String, Object> colorAttrs = attrs(codes, CodeGroups.COLOR, color);
    String slot = categoryAttrs.get("slot") instanceof String s ? s : null;
    return new OutfitItem(
        id,
        name,
        slot,
        category,
        subType,
        color,
        subColors == null ? List.of() : List.copyOf(subColors),
        pattern,
        seasons == null ? List.of() : List.copyOf(seasons),
        styles == null ? List.of() : List.copyOf(styles),
        formality,
        number(subTypeAttrs.get("warmth")).intValue(),
        number(subTypeAttrs.get("volume")).intValue(),
        Boolean.TRUE.equals(colorAttrs.get("neutral")),
        number(colorAttrs.get("light")).doubleValue(),
        wornCount,
        lastWornAt,
        status,
        Boolean.TRUE.equals(attrs(codes, CodeGroups.ITEM_STATUS, status).get("recommendable")));
  }

  private static Map<String, Object> attrs(CodeSnapshot codes, String group, String code) {
    Optional<CodeEntry> entry = codes.find(group, code);
    return entry.map(CodeEntry::attrs).filter(a -> a != null).orElse(Map.of());
  }

  private static Number number(Object value) {
    return value instanceof Number n ? n : 0;
  }
}
