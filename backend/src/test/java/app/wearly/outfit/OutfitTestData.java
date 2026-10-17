package app.wearly.outfit;

import app.wearly.support.TestApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** 코디 추천 통합 테스트용 옷장과 요청 */
final class OutfitTestData {

  static final String PATH = "/api/v1/outfits/recommendations";

  private OutfitTestData() {}

  static Map<String, Object> item(String name, String category, String subType, String color) {
    return item(name, category, subType, color, "OWNED");
  }

  static Map<String, Object> item(
      String name, String category, String subType, String color, String status) {
    Map<String, Object> m = new HashMap<>();
    m.put("name", name);
    m.put("category", category);
    m.put("subType", subType);
    m.put("color", color);
    m.put("pattern", "SOLID");
    m.put("seasons", List.of("SPRING", "FALL"));
    m.put("styles", List.of("NEAT"));
    m.put("formality", 2);
    m.put("status", status);
    return m;
  }

  /** 상의 2 · 하의 2 · 원피스 1 · 아우터 1 · 신발 2 · 가방 1 · 세탁 중 상의 1. 이름 → id */
  static Map<String, String> wardrobe(TestApi api, String token) {
    List<Map<String, Object>> items = new ArrayList<>();
    items.add(item("화이트 셔츠", "TOP", "SHIRT", "white"));
    items.add(item("네이비 니트", "TOP", "KNIT", "navy"));
    items.add(item("차콜 슬랙스", "BOTTOM", "SLACKS", "charcoal"));
    items.add(item("베이지 와이드", "BOTTOM", "WIDE", "beige"));
    items.add(item("블랙 셔츠 원피스", "DRESS", "SHIRT_DRESS", "black"));
    items.add(item("네이비 블레이저", "OUTER", "BLAZER", "navy"));
    items.add(item("블랙 로퍼", "SHOES", "LOAFER", "black"));
    items.add(item("화이트 스니커즈", "SHOES", "SNEAKER", "white"));
    items.add(item("브라운 토트", "BAG", "TOTE", "brown"));
    items.add(item("핑크 블라우스", "TOP", "BLOUSE", "pink", "LAUNDRY"));
    return create(api, token, items);
  }

  static Map<String, String> create(TestApi api, String token, List<Map<String, Object>> items) {
    JsonNode created =
        api.post("/api/v1/wardrobe/items/bulk", token, Map.of("items", items)).expect(201).body();
    Map<String, String> ids = new LinkedHashMap<>();
    for (JsonNode i : created.get("items")) {
      ids.put(i.get("name").asString(), i.get("id").asString());
    }
    return ids;
  }

  static Map<String, Object> context() {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("date", "2026-10-17");
    c.put("occasion", "COMMUTE");
    c.put("mood", "NEAT");
    c.put("formality", "BIZ_CASUAL");
    c.put("weather", Map.of("temp", 14, "min", 8, "max", 19, "rainProb", 20, "cond", "CLOUDY"));
    return c;
  }

  static Map<String, Object> request(Map<String, Object> context) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("context", context);
    return r;
  }
}
