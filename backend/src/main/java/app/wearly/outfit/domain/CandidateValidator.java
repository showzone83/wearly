package app.wearly.outfit.domain;

import static app.wearly.outfit.domain.OutfitCodes.BOTTOM;
import static app.wearly.outfit.domain.OutfitCodes.DRESS;
import static app.wearly.outfit.domain.OutfitCodes.SHOES;
import static app.wearly.outfit.domain.OutfitCodes.SLOTS;
import static app.wearly.outfit.domain.OutfitCodes.TOP;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI가 준 후보 검증 (09 문서 3장 "검증·응답"). 공급자와 무관하게 같은 규칙으로 보고 틀린 후보는 버린다.
 *
 * <ol>
 *   <li>칸 이름이 6칸 중 하나이고, id가 그 회원의 옷이며, 칸이 아이템 종류의 칸(CATEGORY slot)과 같다
 *   <li>고정·중심이 아닌 옷은 추천 가능한 상태이고, 제외 목록·피하고 싶은 색이 아니다
 *   <li>TOP+BOTTOM 또는 DRESS 중 하나 (섞지 않음), SHOES 있음
 *   <li>고정 칸은 고정한 아이템, 중심 아이템 포함
 *   <li>앞 후보와 같은 조합이 아니다
 *   <li>reason은 비지 않은 문자열(80자로 자름), aiScores는 5개 숫자(0~100 정수로 맞춤)
 * </ol>
 */
public final class CandidateValidator {

  public static final int REASON_MAX = 80;

  /** 남은 후보(요청 수까지)와 버린 까닭 */
  public record Result(List<OutfitCandidate> accepted, List<String> rejections) {}

  private CandidateValidator() {}

  public static Result validate(Map<String, Object> json, OutfitPool pool) {
    List<OutfitCandidate> accepted = new ArrayList<>();
    List<String> rejections = new ArrayList<>();
    Object raw = json == null ? null : json.get("candidates");
    if (!(raw instanceof List<?> list)) {
      rejections.add("candidates가 배열이 아니에요.");
      return new Result(accepted, rejections);
    }
    Set<Map<String, String>> seen = new HashSet<>();
    int index = 0;
    for (Object element : list) {
      String label = "후보 " + (index++ + 1) + ": ";
      if (accepted.size() >= pool.request().count()) {
        break;
      }
      try {
        OutfitCandidate candidate = candidate(element, pool);
        if (!seen.add(candidate.items())) {
          throw new Rejected("앞 후보와 같은 조합이에요.");
        }
        accepted.add(candidate);
      } catch (Rejected e) {
        rejections.add(label + e.getMessage());
      }
    }
    return new Result(accepted, rejections);
  }

  private static OutfitCandidate candidate(Object element, OutfitPool pool) {
    if (!(element instanceof Map<?, ?> c)) {
      throw new Rejected("객체가 아니에요.");
    }
    Map<String, String> items = items(c.get("items"), pool);
    boolean separates = items.containsKey(TOP) && items.containsKey(BOTTOM);
    boolean dressOnly =
        items.containsKey(DRESS) && !items.containsKey(TOP) && !items.containsKey(BOTTOM);
    if (separates ? items.containsKey(DRESS) : !dressOnly) {
      throw new Rejected("상의+하의 또는 원피스 하나로 채워야 해요.");
    }
    if (!items.containsKey(SHOES)) {
      throw new Rejected("신발이 없어요.");
    }
    OutfitRequest request = pool.request();
    request
        .fixed()
        .forEach(
            (slot, id) -> {
              if (!id.equals(items.get(slot))) {
                throw new Rejected(slot + " 칸에 고정한 아이템이 아니에요.");
              }
            });
    if (request.anchorItemId() != null && !items.containsValue(request.anchorItemId())) {
      throw new Rejected("중심 아이템이 빠졌어요.");
    }
    return new OutfitCandidate(items, reason(c.get("reason")), scores(c.get("aiScores")));
  }

  private static Map<String, String> items(Object raw, OutfitPool pool) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new Rejected("items가 객체가 아니에요.");
    }
    for (Object key : map.keySet()) {
      if (!SLOTS.contains(String.valueOf(key))) {
        throw new Rejected("모르는 칸이에요: " + key);
      }
    }
    OutfitRequest request = pool.request();
    Map<String, String> items = new LinkedHashMap<>();
    for (String slot : SLOTS) {
      Object value = map.get(slot);
      if (value == null) {
        continue;
      }
      if (!(value instanceof String id) || id.isBlank()) {
        throw new Rejected(slot + " 값이 아이템 id가 아니에요.");
      }
      OutfitItem item =
          pool.owned(id).orElseThrow(() -> new Rejected(slot + "의 " + id + "는 내 옷장에 없어요."));
      if (!slot.equals(item.slot())) {
        throw new Rejected(id + "는 " + slot + " 칸에 들어갈 수 없어요 (종류의 칸: " + item.slot() + ").");
      }
      if (!pool.isPinned(id)) {
        if (!item.recommendable()) {
          throw new Rejected(id + "는 추천할 수 없는 상태예요 (" + item.status() + ").");
        }
        if (request.excludeItemIds().contains(id)) {
          throw new Rejected(id + "는 제외한 아이템이에요.");
        }
        if (request.avoidColors().contains(item.color())) {
          throw new Rejected(id + "는 피하고 싶은 색(" + item.color() + ")이에요.");
        }
      }
      items.put(slot, id);
    }
    return items;
  }

  private static String reason(Object raw) {
    if (!(raw instanceof String s) || s.isBlank()) {
      throw new Rejected("reason이 없어요.");
    }
    String reason = s.strip().replaceAll("\\s+", " ");
    if (reason.codePointCount(0, reason.length()) > REASON_MAX) {
      reason = reason.substring(0, reason.offsetByCodePoints(0, REASON_MAX)).strip();
    }
    return reason;
  }

  private static AiScores scores(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new Rejected("aiScores가 객체가 아니에요.");
    }
    int[] v = new int[AiScores.FIELDS.size()];
    for (int i = 0; i < v.length; i++) {
      String field = AiScores.FIELDS.get(i);
      if (!(map.get(field) instanceof Number n) || Double.isNaN(n.doubleValue())) {
        throw new Rejected("aiScores." + field + "가 숫자가 아니에요.");
      }
      v[i] = (int) Math.max(0, Math.min(100, Math.round(n.doubleValue())));
    }
    return new AiScores(v[0], v[1], v[2], v[3], v[4]);
  }

  /** 후보 하나를 버리는 까닭 */
  private static final class Rejected extends RuntimeException {

    Rejected(String message) {
      super(message, null, false, false);
    }
  }
}
