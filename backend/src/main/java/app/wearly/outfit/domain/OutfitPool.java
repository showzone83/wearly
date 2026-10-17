package app.wearly.outfit.domain;

import static app.wearly.outfit.domain.OutfitCodes.BOTTOM;
import static app.wearly.outfit.domain.OutfitCodes.DRESS;
import static app.wearly.outfit.domain.OutfitCodes.SHOES;
import static app.wearly.outfit.domain.OutfitCodes.SLOTS;
import static app.wearly.outfit.domain.OutfitCodes.TOP;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 이번 추천에서 칸별로 쓸 수 있는 옷 (앱 규칙 엔진 eligibleBySlot과 같은 규칙).
 *
 * <ul>
 *   <li>칸(CATEGORY slot)이 있고 추천 가능한 상태(ITEM_STATUS recommendable)인 옷만
 *   <li>제외 목록(excludeItemIds)·피하고 싶은 색(avoidColors)은 뺀다
 *   <li>고정·중심 아이템은 상태·제외·색과 관계없이 넣고, 그 칸은 그 아이템 하나만 쓴다
 *   <li>원피스를 고정(또는 중심)하면 상의·하의를 쓰지 않고, 상의·하의를 고정하면 원피스를 쓰지 않는다
 * </ul>
 */
public final class OutfitPool {

  private final OutfitRequest request;
  private final Map<String, OutfitItem> owned;
  private final Map<String, List<OutfitItem>> bySlot;
  private final Map<String, String> pinnedBySlot;
  private final boolean wantsDress;
  private final boolean wantsSeparates;

  private OutfitPool(OutfitRequest request, List<OutfitItem> items) {
    this.request = request;
    Map<String, OutfitItem> byId = new LinkedHashMap<>();
    items.forEach(i -> byId.put(i.id(), i));
    this.owned = byId;

    Set<String> pinned = request.pinnedIds();
    Map<String, String> pins = new LinkedHashMap<>();
    for (String id : pinned) {
      OutfitItem item = byId.get(id);
      if (item != null && item.slot() != null) {
        pins.put(item.slot(), id);
      }
    }
    this.pinnedBySlot = pins;
    this.wantsDress = pins.containsKey(DRESS);
    this.wantsSeparates = pins.containsKey(TOP) || pins.containsKey(BOTTOM);

    Map<String, List<OutfitItem>> slots = new LinkedHashMap<>();
    SLOTS.forEach(s -> slots.put(s, new ArrayList<>()));
    // 프롬프트·목업이 늘 같은 순서를 보도록 id 순서로 둔다
    items.stream()
        .filter(i -> i.slot() != null && slots.containsKey(i.slot()))
        .sorted(Comparator.comparing(OutfitItem::id, OutfitPool::compareIds))
        .forEach(
            i -> {
              String pin = pins.get(i.slot());
              if (pin != null ? pin.equals(i.id()) : usable(i)) {
                slots.get(i.slot()).add(i);
              }
            });
    slots.replaceAll((k, v) -> List.copyOf(v));
    this.bySlot = slots;
  }

  /** 회원의 옷장 전체와 요청으로 만든다. 고정·중심 아이템은 checkPins로 먼저 확인한다 */
  public static OutfitPool of(OutfitRequest request, List<OutfitItem> items) {
    return new OutfitPool(request, items);
  }

  /**
   * 고정·중심 아이템 확인: 내 옷장에 있고, 코디 칸이 있고, 고정 칸이 아이템 종류의 칸과 같고, 원피스와 상의·하의를 함께 고정하지 않는다.
   *
   * @return 틀린 곳 (fieldErrors). 없으면 빈 목록
   */
  public static List<FieldError> checkPins(OutfitRequest request, List<OutfitItem> items) {
    Map<String, OutfitItem> byId = new LinkedHashMap<>();
    items.forEach(i -> byId.put(i.id(), i));
    List<FieldError> errors = new ArrayList<>();
    Map<String, String> slotOf = new LinkedHashMap<>();
    request
        .fixed()
        .forEach(
            (slot, id) -> {
              String field = "context.fixed." + slot;
              OutfitItem item = byId.get(id);
              if (item == null) {
                errors.add(new FieldError(field, "내 옷장에 없는 아이템이에요."));
              } else if (!slot.equals(item.slot())) {
                errors.add(new FieldError(field, slot + " 칸에 넣을 수 없는 아이템이에요."));
              } else {
                slotOf.put(slot, id);
              }
            });
    String anchor = request.anchorItemId();
    if (anchor != null) {
      OutfitItem item = byId.get(anchor);
      if (item == null) {
        errors.add(new FieldError("context.anchorItemId", "내 옷장에 없는 아이템이에요."));
      } else if (item.slot() == null) {
        errors.add(new FieldError("context.anchorItemId", "코디에 넣을 수 없는 종류예요."));
      } else if (slotOf.containsKey(item.slot()) && !slotOf.get(item.slot()).equals(anchor)) {
        errors.add(new FieldError("context.anchorItemId", "같은 칸에 고정한 아이템과 달라요."));
      } else {
        slotOf.put(item.slot(), anchor);
      }
    }
    if (slotOf.containsKey(DRESS) && (slotOf.containsKey(TOP) || slotOf.containsKey(BOTTOM))) {
      errors.add(new FieldError("context.fixed", "원피스와 상의·하의를 함께 고정할 수 없어요."));
    }
    return errors;
  }

  /** 고정·중심이 아닌 옷이 이번 추천에 쓰일 수 있는지 */
  private boolean usable(OutfitItem i) {
    return i.recommendable()
        && !request.excludeItemIds().contains(i.id())
        && !request.avoidColors().contains(i.color());
  }

  /** 숫자 id는 숫자 순서, 그 밖은 문자열 순서 */
  static int compareIds(String a, String b) {
    boolean na = a.chars().allMatch(Character::isDigit) && !a.isEmpty() && a.length() < 19;
    boolean nb = b.chars().allMatch(Character::isDigit) && !b.isEmpty() && b.length() < 19;
    if (na && nb) {
      return Long.compare(Long.parseLong(a), Long.parseLong(b));
    }
    return na != nb ? (na ? -1 : 1) : a.compareTo(b);
  }

  public OutfitRequest request() {
    return request;
  }

  /** 그 칸에 쓸 수 있는 옷 (id 순서) */
  public List<OutfitItem> eligible(String slot) {
    return bySlot.getOrDefault(slot, List.of());
  }

  /** 회원 옷장의 아이템 (칸·상태와 관계없이) */
  public Optional<OutfitItem> owned(String id) {
    return Optional.ofNullable(owned.get(id));
  }

  /** 그 칸에 고정(또는 중심)한 아이템 id */
  public Optional<String> pinned(String slot) {
    return Optional.ofNullable(pinnedBySlot.get(slot));
  }

  public boolean isPinned(String id) {
    return pinnedBySlot.containsValue(id);
  }

  /** 상의+하의 조합을 만들 수 있는지 */
  public boolean separatesAvailable() {
    return !wantsDress && !eligible(TOP).isEmpty() && !eligible(BOTTOM).isEmpty();
  }

  /** 원피스로 몸판을 채울 수 있는지 */
  public boolean dressAvailable() {
    return !wantsSeparates && !eligible(DRESS).isEmpty();
  }

  /**
   * 코디를 만들 수 없을 때 모자란 칸. 칸 코드로 쓴다 (앱이 칸 이름으로 바꾼다). 예: ["BOTTOM 또는 DRESS", "SHOES"]
   *
   * @return 코디를 만들 수 있으면 빈 목록
   */
  public List<String> missing() {
    List<String> out = new ArrayList<>();
    if (!separatesAvailable() && !dressAvailable() && !wantsDress) {
      List<String> parts = new ArrayList<>();
      if (eligible(TOP).isEmpty()) {
        parts.add(TOP);
      }
      if (eligible(BOTTOM).isEmpty()) {
        parts.add(BOTTOM);
      }
      out.add(String.join("·", parts) + (wantsSeparates ? "" : " 또는 " + DRESS));
    }
    if (eligible(SHOES).isEmpty()) {
      out.add(SHOES);
    }
    return out;
  }
}
