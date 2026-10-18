package app.wearly.plan.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 예정에 붙은 코디 (앱 Outfit 모양 그대로 JSONB). 서버가 아는 것은 items(칸 → 아이템 ID)뿐이고 나머지(점수·이유·추천 조건)는 앱 값이다. 크기만
 * 제한한다.
 */
public final class PlanOutfits {

  /** 코디 칸 (앱 Slot과 같다) */
  public static final List<String> SLOTS =
      List.of("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG");

  /** 아이템 ID 최대 글자 수 (서버 ID는 숫자 문자열, 기기 ID는 item-xxxx) */
  public static final int ITEM_ID_MAX = 64;

  /** JSON으로 썼을 때의 최대 글자 수 (대략값) */
  public static final int MAX_JSON_LENGTH = 16_384;

  /** 객체·배열이 겹칠 수 있는 깊이 */
  static final int MAX_DEPTH = 10;

  private PlanOutfits() {}

  /** null이면 검사하지 않는다 (코디 없음) */
  public static void validate(Map<String, Object> outfit, String prefix, List<FieldError> errors) {
    if (outfit == null) {
      return;
    }
    if (jsonLength(outfit, 0) > MAX_JSON_LENGTH) {
      errors.add(FieldError.tooLong(prefix + "outfit", MAX_JSON_LENGTH));
      return;
    }
    String field = prefix + "outfit.items";
    Object items = outfit.get("items");
    if (items == null) {
      errors.add(FieldError.required(field));
      return;
    }
    if (!(items instanceof Map<?, ?> map)) {
      errors.add(FieldError.invalidType(field, "object"));
      return;
    }
    for (Map.Entry<?, ?> e : map.entrySet()) {
      String slot = String.valueOf(e.getKey());
      String name = field + "." + slot;
      if (!SLOTS.contains(slot)) {
        errors.add(FieldError.notAllowed(name, SLOTS));
        continue;
      }
      Object v = e.getValue();
      if (v == null || (v instanceof String s && s.isBlank())) {
        errors.add(FieldError.required(name));
      } else if (!(v instanceof String s)) {
        errors.add(FieldError.invalidType(name, "string"));
      } else if (s.length() > ITEM_ID_MAX) {
        errors.add(FieldError.tooLong(name, ITEM_ID_MAX));
      }
    }
  }

  /** 코디에 든 아이템 ID (칸 순서, 중복 없이). 코디가 없으면 빈 목록 */
  public static List<String> itemIds(Map<String, Object> outfit) {
    if (outfit == null || !(outfit.get("items") instanceof Map<?, ?> items)) {
      return List.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    for (String slot : SLOTS) {
      if (items.get(slot) instanceof String id && !id.isBlank()) {
        ids.add(id);
      }
    }
    return List.copyOf(ids);
  }

  /** 서버 아이템 ID(양의 정수 문자열)면 그 숫자, 아니면 null (기기에서 만든 ID 등) */
  public static Long serverItemId(String id) {
    if (id == null || id.isEmpty() || id.length() > 18) {
      return null;
    }
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c < '0' || c > '9') {
        return null;
      }
    }
    long value = Long.parseLong(id);
    return value > 0 ? value : null;
  }

  /** 코디 아이템 중 지금 옷장에 없는 ID (서버 ID가 아닌 값 포함) */
  public static List<String> missing(Map<String, Object> outfit, Collection<Long> existing) {
    List<String> out = new ArrayList<>();
    for (String id : itemIds(outfit)) {
      Long n = serverItemId(id);
      if (n == null || !existing.contains(n)) {
        out.add(id);
      }
    }
    return out;
  }

  /** JSON으로 썼을 때의 대략 글자 수. 너무 깊으면 상한을 넘은 것으로 본다 */
  static int jsonLength(Object v, int depth) {
    if (depth > MAX_DEPTH) {
      return MAX_JSON_LENGTH + 1;
    }
    if (v == null) {
      return 4;
    }
    if (v instanceof String s) {
      return s.length() + 2;
    }
    if (v instanceof Map<?, ?> m) {
      int n = 2;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        n += String.valueOf(e.getKey()).length() + 4 + jsonLength(e.getValue(), depth + 1);
        if (n > MAX_JSON_LENGTH) {
          return n;
        }
      }
      return n;
    }
    if (v instanceof Collection<?> c) {
      int n = 2;
      for (Object o : c) {
        n += 1 + jsonLength(o, depth + 1);
        if (n > MAX_JSON_LENGTH) {
          return n;
        }
      }
      return n;
    }
    return String.valueOf(v).length();
  }
}
