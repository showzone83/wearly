package app.wearly.code.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.sharedkernel.code.Slots;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 그룹별 attrs 검증과 정규화 (06 문서 3장, 07 문서 4장). 틀린 값은 attrs.키 필드 오류로 모은다. 그룹에 없는 키도 거부한다. */
public final class CodeAttrs {

  static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  private CodeAttrs() {}

  /**
   * @param self COLOR pairs에서 자기 자신을 막기 위한 코드
   * @return 저장할 attrs (키 순서 고정, COLOR pairs는 중복 제거·정렬, hex는 대문자)
   */
  public static Map<String, Object> validate(
      String group,
      Map<String, Object> attrs,
      String self,
      CodeSnapshot codes,
      List<FieldError> errors) {
    Map<String, Object> in = attrs == null ? Map.of() : attrs;
    Map<String, Object> out = new LinkedHashMap<>();
    Set<String> allowed =
        switch (group) {
          case CodeGroups.CATEGORY -> Set.of("slot");
          case CodeGroups.SUB_TYPE -> Set.of("volume", "warmth", "shape");
          case CodeGroups.COLOR -> Set.of("hex", "neutral", "light", "pairs");
          case CodeGroups.ITEM_STATUS -> Set.of("recommendable");
          case CodeGroups.REGION -> Set.of("lat", "lon");
          default -> Set.of();
        };
    for (String key : in.keySet()) {
      if (!allowed.contains(key)) {
        errors.add(new FieldError("attrs." + key, "이 그룹에 없는 속성이에요."));
      }
    }
    switch (group) {
      case CodeGroups.CATEGORY -> {
        Object slot = in.get("slot");
        if (slot != null && !(slot instanceof String s && Slots.ALL.contains(s))) {
          errors.add(new FieldError("attrs.slot", String.join("·", Slots.ALL) + " 또는 null이어야 해요."));
        }
        out.put("slot", slot);
      }
      case CodeGroups.SUB_TYPE -> {
        out.put("volume", integer(in, "volume", -1, 1, errors));
        out.put("warmth", integer(in, "warmth", 0, 3, errors));
        Object shape = in.get("shape");
        if (!(shape instanceof String s) || s.isBlank() || s.length() > 30) {
          errors.add(new FieldError("attrs.shape", "1~30자 문자열이어야 해요."));
        }
        out.put("shape", shape instanceof String s ? s.strip() : shape);
      }
      case CodeGroups.COLOR -> {
        Object hex = in.get("hex");
        if (!(hex instanceof String s) || !HEX.matcher(s).matches()) {
          errors.add(new FieldError("attrs.hex", "#RRGGBB 형식이어야 해요."));
          out.put("hex", hex);
        } else {
          out.put("hex", s.toUpperCase(Locale.ROOT));
        }
        out.put("neutral", bool(in, "neutral", errors));
        Object light = in.get("light");
        if (!(light instanceof Number n)
            || Double.isNaN(n.doubleValue())
            || n.doubleValue() < 0
            || n.doubleValue() > 1) {
          errors.add(new FieldError("attrs.light", "0~1 숫자여야 해요."));
        }
        out.put("light", light);
        out.put("pairs", pairs(in.get("pairs"), self, codes, errors));
      }
      case CodeGroups.ITEM_STATUS -> out.put("recommendable", bool(in, "recommendable", errors));
      case CodeGroups.REGION -> {
        out.put("lat", number(in, "lat", -90, 90, errors));
        out.put("lon", number(in, "lon", -180, 180, errors));
      }
      default -> {
        // PATTERN·SEASON·STYLE: attrs 없음
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  public static List<String> pairsOf(Map<String, Object> attrs) {
    Object v = attrs == null ? null : attrs.get("pairs");
    if (v instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      list.forEach(
          x -> {
            if (x instanceof String s) {
              out.add(s);
            }
          });
      return out;
    }
    return List.of();
  }

  private static List<String> pairs(
      Object value, String self, CodeSnapshot codes, List<FieldError> errors) {
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> list)) {
      errors.add(new FieldError("attrs.pairs", "색 코드 배열이어야 해요."));
      return List.of();
    }
    TreeSet<String> out = new TreeSet<>();
    for (Object o : list) {
      if (!(o instanceof String s) || !codes.known(CodeGroups.COLOR, s)) {
        errors.add(new FieldError("attrs.pairs", "있는 색 코드만 넣을 수 있어요: " + o));
      } else if (s.equals(self)) {
        errors.add(new FieldError("attrs.pairs", "자기 자신은 넣을 수 없어요."));
      } else {
        out.add(s);
      }
    }
    return List.copyOf(out);
  }

  private static Object integer(
      Map<String, Object> in, String key, int min, int max, List<FieldError> errors) {
    Object v = in.get(key);
    if (!(v instanceof Integer || v instanceof Long)
        || ((Number) v).longValue() < min
        || ((Number) v).longValue() > max) {
      errors.add(new FieldError("attrs." + key, min + "~" + max + " 정수여야 해요."));
      return v;
    }
    return ((Number) v).intValue();
  }

  private static Object number(
      Map<String, Object> in, String key, double min, double max, List<FieldError> errors) {
    Object v = in.get(key);
    if (!(v instanceof Number n)
        || Double.isNaN(n.doubleValue())
        || n.doubleValue() < min
        || n.doubleValue() > max) {
      errors.add(new FieldError("attrs." + key, (int) min + "~" + (int) max + " 숫자여야 해요."));
    }
    return v;
  }

  private static Object bool(Map<String, Object> in, String key, List<FieldError> errors) {
    Object v = in.get(key);
    if (!(v instanceof Boolean)) {
      errors.add(new FieldError("attrs." + key, "true 또는 false여야 해요."));
    }
    return v;
  }
}
