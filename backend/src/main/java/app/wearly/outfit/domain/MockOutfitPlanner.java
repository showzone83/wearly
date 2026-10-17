package app.wearly.outfit.domain;

import static app.wearly.outfit.domain.OutfitCodes.BAG;
import static app.wearly.outfit.domain.OutfitCodes.BOTTOM;
import static app.wearly.outfit.domain.OutfitCodes.DRESS;
import static app.wearly.outfit.domain.OutfitCodes.OUTER;
import static app.wearly.outfit.domain.OutfitCodes.SHOES;
import static app.wearly.outfit.domain.OutfitCodes.TOP;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 목업 실행(WEARLY_AI_PROVIDER=mock)의 결정적 응답 (09 문서 4장, 하네스용). 같은 옷장·같은 요청이면 같은 후보다. 칸별
 * 목록(OutfitPool)에서 규칙에 맞게 고르므로 검증(CandidateValidator)을 그대로 통과한다.
 *
 * <ul>
 *   <li>몸판: 상의×하의 조합(id 순) 다음 원피스. 후보마다 다른 몸판부터 쓴다
 *   <li>신발: 후보 순서대로 돌려 쓴다. 아우터: 고정했거나 날씨가 아우터를 원할 때만. 가방: 있으면 돌려 쓴다
 * </ul>
 */
public final class MockOutfitPlanner {

  private MockOutfitPlanner() {}

  /** 공급자 응답과 같은 모양: { candidates: [{ items, reason, aiScores }] } */
  public static Map<String, Object> plan(OutfitPool pool) {
    OutfitRequest r = pool.request();
    List<Map<String, String>> bodies = new ArrayList<>();
    if (pool.separatesAvailable()) {
      for (OutfitItem top : pool.eligible(TOP)) {
        for (OutfitItem bottom : pool.eligible(BOTTOM)) {
          Map<String, String> body = new LinkedHashMap<>();
          body.put(TOP, top.id());
          body.put(BOTTOM, bottom.id());
          bodies.add(body);
        }
      }
    }
    if (pool.dressAvailable()) {
      pool.eligible(DRESS).forEach(d -> bodies.add(new LinkedHashMap<>(Map.of(DRESS, d.id()))));
    }
    List<OutfitItem> shoes = pool.eligible(SHOES);
    List<OutfitItem> outers = pool.eligible(OUTER);
    List<OutfitItem> bags = pool.eligible(BAG);
    boolean outer =
        pool.pinned(OUTER).isPresent() || (r.weather() != null && r.weather().wantsOuter());

    List<Object> candidates = new ArrayList<>();
    Set<Map<String, Object>> seen = new LinkedHashSet<>();
    int total = bodies.size() * Math.max(1, shoes.size());
    for (int k = 0; k < total && candidates.size() < r.count() && !shoes.isEmpty(); k++) {
      int b = k % bodies.size();
      final int round = k / bodies.size();
      Map<String, Object> items = new LinkedHashMap<>();
      for (String slot : OutfitCodes.SLOTS) {
        items.put(slot, null);
      }
      items.putAll(bodies.get(b));
      items.put(OUTER, outer && !outers.isEmpty() ? pick(outers, k).id() : null);
      items.put(SHOES, pick(shoes, b + round).id());
      items.put(BAG, bags.isEmpty() ? null : pick(bags, k).id());
      if (!seen.add(items)) {
        continue;
      }
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("items", items);
      c.put("reason", reason(pool, items));
      int score = Math.max(50, 85 - candidates.size() * 5);
      Map<String, Object> scores = new LinkedHashMap<>();
      AiScores.FIELDS.forEach(f -> scores.put(f, score));
      c.put("aiScores", scores);
      candidates.add(c);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("candidates", candidates);
    return out;
  }

  private static OutfitItem pick(List<OutfitItem> items, int k) {
    return items.get(Math.floorMod(k, items.size()));
  }

  private static String reason(OutfitPool pool, Map<String, Object> items) {
    List<String> names = new ArrayList<>();
    for (String slot : List.of(TOP, BOTTOM, DRESS, OUTER)) {
      Optional.ofNullable(items.get(slot))
          .flatMap(id -> pool.owned((String) id))
          .ifPresent(i -> names.add(i.name()));
    }
    String text = "목업 추천: " + String.join(" + ", names) + " 조합이에요.";
    return text.codePointCount(0, text.length()) <= CandidateValidator.REASON_MAX
        ? text
        : text.substring(0, text.offsetByCodePoints(0, CandidateValidator.REASON_MAX - 1)) + "…";
  }
}
