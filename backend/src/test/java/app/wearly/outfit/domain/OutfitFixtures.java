package app.wearly.outfit.domain;

import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.support.TestCodes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 코디 추천 단위 테스트용 옷장·요청 (Spring 없음, 코드는 doc/06_코드_초기값.json) */
final class OutfitFixtures {

  static final CodeSnapshot CODES = TestCodes.seed();
  static final LocalDate TODAY = LocalDate.parse("2026-10-17");

  private OutfitFixtures() {}

  static OutfitItem item(String id, String category, String subType, String color) {
    return item(id, category, subType, color, "OWNED");
  }

  static OutfitItem item(String id, String category, String subType, String color, String status) {
    return item(id, category, subType, color, List.of(), status);
  }

  /** 보조 색이 있는 옷 (이슈 #4) */
  static OutfitItem item(
      String id,
      String category,
      String subType,
      String color,
      List<String> subColors,
      String status) {
    return OutfitItem.of(
        id,
        "옷" + id,
        category,
        subType,
        color,
        subColors,
        "SOLID",
        List.of("SPRING", "FALL"),
        List.of("NEAT"),
        2,
        status,
        1,
        TODAY.minusDays(10),
        CODES);
  }

  /** 상의 2 · 하의 2 · 원피스 1 · 아우터 1 · 신발 2 · 가방 1 · 세탁 중 상의 1 · 액세서리 1 */
  static List<OutfitItem> wardrobe() {
    List<OutfitItem> items = new ArrayList<>();
    items.add(item("1", "TOP", "SHIRT", "white"));
    items.add(item("2", "TOP", "KNIT", "navy"));
    items.add(item("3", "BOTTOM", "SLACKS", "charcoal"));
    items.add(item("4", "BOTTOM", "WIDE", "beige"));
    items.add(item("5", "DRESS", "SHIRT_DRESS", "black"));
    items.add(item("6", "OUTER", "BLAZER", "navy"));
    items.add(item("7", "SHOES", "LOAFER", "black"));
    items.add(item("8", "SHOES", "SNEAKER", "white"));
    items.add(item("9", "BAG", "TOTE", "brown"));
    items.add(item("10", "TOP", "BLOUSE", "pink", "LAUNDRY"));
    items.add(item("11", "ACC", "WATCH", "black"));
    return items;
  }

  static OutfitRequest request() {
    return request(Map.of(), null, Set.of(), List.of(), 5);
  }

  static OutfitRequest request(
      Map<String, String> fixed,
      String anchor,
      Set<String> exclude,
      List<String> avoidColors,
      int count) {
    return new OutfitRequest(
        TODAY,
        "COMMUTE",
        "NEAT",
        "BIZ_CASUAL",
        new OutfitRequest.Weather(14, 8, 19, 20, "CLOUDY"),
        anchor,
        new LinkedHashMap<>(fixed),
        avoidColors,
        List.of(),
        null,
        null,
        exclude,
        List.of(),
        Set.of(),
        Set.of(),
        count);
  }

  /** 공급자 응답 모양의 후보 하나 */
  static Map<String, Object> candidate(Map<String, String> items) {
    Map<String, Object> c = new LinkedHashMap<>();
    Map<String, Object> slots = new LinkedHashMap<>();
    for (String slot : OutfitCodes.SLOTS) {
      slots.put(slot, items.get(slot));
    }
    c.put("items", slots);
    c.put("reason", "네이비 니트와 차콜 슬랙스로 단정하게 맞췄어요.");
    Map<String, Object> scores = new LinkedHashMap<>();
    scores.put("color", 80);
    scores.put("occasion", 75.4);
    scores.put("weather", 120);
    scores.put("taste", -3);
    scores.put("diversity", 60);
    c.put("aiScores", scores);
    return c;
  }

  static Map<String, Object> response(Object... candidates) {
    return Map.of("candidates", List.of(candidates));
  }
}
