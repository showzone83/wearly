package app.wearly.code.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import app.wearly.sharedkernel.code.Slots;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 그룹별 attrs 검증과 정규화 (06 문서 3장, 07 문서 4장, MALL은 24 문서). 틀린 값은 attrs.키 필드 오류로 모은다. 그룹에 없는 키도 거부한다.
 */
public final class CodeAttrs {

  static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  static final int SHAPE_MAX = 30;

  /** BODY_TYPE attrs.description·hint 최대 길이 (앱 체형 고르기 화면에 한두 줄로 보인다) */
  static final int TEXT_MAX = 80;

  /** MALL attrs.url 최대 길이 */
  static final int URL_MAX = 500;

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
          case CodeGroups.BODY_TYPE -> Set.of("description", "hint");
          case CodeGroups.MALL -> Set.of("url");
          default -> Set.of();
        };
    for (String key : in.keySet()) {
      if (!allowed.contains(key)) {
        errors.add(FieldError.notAllowed("attrs." + key));
      }
    }
    switch (group) {
      case CodeGroups.CATEGORY -> {
        Object slot = in.get("slot");
        if (slot != null && !(slot instanceof String s && Slots.ALL.contains(s))) {
          // 칸 코드 또는 null
          errors.add(FieldError.notAllowed("attrs.slot", Slots.ALL));
        }
        out.put("slot", slot);
      }
      case CodeGroups.SUB_TYPE -> {
        out.put("volume", integer(in, "volume", -1, 1, errors));
        out.put("warmth", integer(in, "warmth", 0, 3, errors));
        Object shape = in.get("shape");
        if (!(shape instanceof String s)) {
          errors.add(
              shape == null
                  ? FieldError.required("attrs.shape")
                  : FieldError.invalidType("attrs.shape", "string"));
        } else if (s.isBlank()) {
          errors.add(FieldError.required("attrs.shape"));
        } else if (s.length() > SHAPE_MAX) {
          errors.add(FieldError.tooLong("attrs.shape", SHAPE_MAX));
        }
        out.put("shape", shape instanceof String s ? s.strip() : shape);
      }
      case CodeGroups.COLOR -> {
        Object hex = in.get("hex");
        if (!(hex instanceof String s) || !HEX.matcher(s).matches()) {
          errors.add(FieldError.invalidFormat("attrs.hex", "#RRGGBB"));
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
          errors.add(
              light instanceof Number
                  ? FieldError.outOfRange("attrs.light", 0, 1)
                  : FieldError.invalidType("attrs.light", "number"));
        }
        out.put("light", light);
        out.put("pairs", pairs(in.get("pairs"), self, codes, errors));
      }
      case CodeGroups.ITEM_STATUS -> out.put("recommendable", bool(in, "recommendable", errors));
      case CodeGroups.REGION -> {
        out.put("lat", number(in, "lat", -90, 90, errors));
        out.put("lon", number(in, "lon", -180, 180, errors));
      }
      case CodeGroups.BODY_TYPE -> {
        out.put("description", text(in, "description", errors));
        out.put("hint", text(in, "hint", errors));
      }
      case CodeGroups.MALL -> out.put("url", url(in, "url", errors));
      default -> {
        // PATTERN·SEASON·STYLE·RETIRE_REASON·AGE_RANGE·FIT: attrs 없음
      }
    }
    return out;
  }

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
      errors.add(FieldError.invalidType("attrs.pairs", "array"));
      return List.of();
    }
    TreeSet<String> out = new TreeSet<>();
    for (Object o : list) {
      if (!(o instanceof String s) || !codes.known(CodeGroups.COLOR, s)) {
        // 없는 색 코드 (받은 값은 응답에 되돌려 쓰지 않는다)
        errors.add(FieldError.notAllowed("attrs.pairs"));
      } else if (s.equals(self)) {
        errors.add(new FieldError("attrs.pairs", FieldErrorCode.SELF_NOT_ALLOWED));
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
      errors.add(
          v instanceof Integer || v instanceof Long
              ? FieldError.outOfRange("attrs." + key, min, max)
              : FieldError.invalidType("attrs." + key, "integer"));
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
      errors.add(
          v instanceof Number
              ? FieldError.outOfRange("attrs." + key, (int) min, (int) max)
              : FieldError.invalidType("attrs." + key, "number"));
    }
    return v;
  }

  /** 필수 문자열 (앞뒤 공백 제거, TEXT_MAX자 이하) */
  private static Object text(Map<String, Object> in, String key, List<FieldError> errors) {
    Object v = in.get(key);
    String field = "attrs." + key;
    if (!(v instanceof String s)) {
      errors.add(v == null ? FieldError.required(field) : FieldError.invalidType(field, "string"));
      return v;
    }
    String t = s.strip();
    if (t.isEmpty()) {
      errors.add(FieldError.required(field));
    } else if (t.codePointCount(0, t.length()) > TEXT_MAX) {
      errors.add(FieldError.tooLong(field, TEXT_MAX));
    }
    return t;
  }

  /**
   * 앱이 웹 화면으로 여는 주소. https만 받고(앱 안 화면이라 평문 http는 막는다), 호스트가 있어야 하며, 사용자 정보(user@)가 든 주소는 받지 않는다
   * (피싱처럼 보이는 주소 방지).
   */
  private static Object url(Map<String, Object> in, String key, List<FieldError> errors) {
    Object v = in.get(key);
    String field = "attrs." + key;
    if (!(v instanceof String s)) {
      errors.add(v == null ? FieldError.required(field) : FieldError.invalidType(field, "string"));
      return v;
    }
    String t = s.strip();
    if (t.isEmpty()) {
      errors.add(FieldError.required(field));
      return t;
    }
    if (t.length() > URL_MAX) {
      errors.add(FieldError.tooLong(field, URL_MAX));
      return t;
    }
    if (!isHttpsUrl(t)) {
      errors.add(FieldError.invalidFormat(field, "url"));
    }
    return t;
  }

  static boolean isHttpsUrl(String s) {
    try {
      URI u = new URI(s);
      return "https".equalsIgnoreCase(u.getScheme())
          && u.getHost() != null
          && !u.getHost().isBlank()
          && u.getRawUserInfo() == null;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static Object bool(Map<String, Object> in, String key, List<FieldError> errors) {
    Object v = in.get(key);
    if (!(v instanceof Boolean)) {
      errors.add(FieldError.invalidType("attrs." + key, "boolean"));
    }
    return v;
  }
}
