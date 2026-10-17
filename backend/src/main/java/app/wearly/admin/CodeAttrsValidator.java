package app.wearly.admin;

import app.wearly.code.CodeGroups;
import app.wearly.common.error.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 그룹별 attrs 검증과 정규화 (doc/06_코드_관리.md 3장, doc/07_관리자_사이트.md 4장).
 * 오류는 fieldErrors의 attrs.키 형식으로 모은다. 그룹에 없는 키는 받지 않는다.
 */
final class CodeAttrsValidator {

    static final Set<String> SLOTS = Set.of("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG");
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private CodeAttrsValidator() {
    }

    /**
     * @param colorExists COLOR pairs 검사용: 있는 COLOR 코드인지
     * @param selfCode    자기 자신 (pairs에 자기를 넣지 못하게)
     */
    static Map<String, Object> normalize(String group, Map<String, Object> raw, Predicate<String> colorExists,
            String selfCode, List<FieldError> errors) {
        Map<String, Object> attrs = raw == null ? Map.of() : raw;
        Map<String, Object> out = new LinkedHashMap<>();
        switch (group) {
            case CodeGroups.CATEGORY -> {
                allowOnly(attrs, Set.of("slot"), errors);
                Object slot = attrs.get("slot");
                if (slot != null && !(slot instanceof String s && SLOTS.contains(s))) {
                    errors.add(new FieldError("attrs.slot", "TOP, BOTTOM, DRESS, OUTER, SHOES, BAG 또는 null이어야 해요."));
                }
                out.put("slot", slot);
            }
            case CodeGroups.SUB_TYPE -> {
                allowOnly(attrs, Set.of("volume", "warmth", "shape"), errors);
                out.put("volume", intIn(attrs, "volume", -1, 1, errors));
                out.put("warmth", intIn(attrs, "warmth", 0, 3, errors));
                Object shape = attrs.get("shape");
                if (!(shape instanceof String s) || s.isBlank() || s.strip().length() > 30) {
                    errors.add(new FieldError("attrs.shape", "1~30자 문자열이어야 해요."));
                } else {
                    out.put("shape", s.strip());
                }
            }
            case CodeGroups.COLOR -> {
                allowOnly(attrs, Set.of("hex", "neutral", "light", "pairs"), errors);
                Object hex = attrs.get("hex");
                if (!(hex instanceof String h) || !HEX.matcher(h).matches()) {
                    errors.add(new FieldError("attrs.hex", "#RRGGBB 형식이어야 해요."));
                } else {
                    out.put("hex", h.toUpperCase());
                }
                Object neutral = attrs.get("neutral");
                if (!(neutral instanceof Boolean)) {
                    errors.add(new FieldError("attrs.neutral", "true 또는 false여야 해요."));
                } else {
                    out.put("neutral", neutral);
                }
                Object light = attrs.get("light");
                if (!(light instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 1) {
                    errors.add(new FieldError("attrs.light", "0~1 숫자여야 해요."));
                } else {
                    out.put("light", light);
                }
                out.put("pairs", pairs(attrs.get("pairs"), colorExists, selfCode, errors));
            }
            case CodeGroups.ITEM_STATUS -> {
                allowOnly(attrs, Set.of("recommendable"), errors);
                Object rec = attrs.get("recommendable");
                if (!(rec instanceof Boolean)) {
                    errors.add(new FieldError("attrs.recommendable", "true 또는 false여야 해요."));
                } else {
                    out.put("recommendable", rec);
                }
            }
            default -> allowOnly(attrs, Set.of(), errors); // PATTERN·SEASON·STYLE: attrs 없음
        }
        return out;
    }

    private static void allowOnly(Map<String, Object> attrs, Set<String> allowed, List<FieldError> errors) {
        attrs.keySet().stream().filter(k -> !allowed.contains(k)).sorted()
                .forEach(k -> errors.add(new FieldError("attrs." + k, "이 그룹에서 쓰지 않는 속성이에요.")));
    }

    private static Integer intIn(Map<String, Object> attrs, String key, int min, int max, List<FieldError> errors) {
        Object v = attrs.get(key);
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= min
                && n.intValue() <= max) {
            return n.intValue();
        }
        errors.add(new FieldError("attrs." + key, min + "~" + max + " 정수여야 해요."));
        return null;
    }

    /** 있는 COLOR 코드만, 자기 자신 제외, 중복 없이 알파벳순 */
    private static List<String> pairs(Object raw, Predicate<String> colorExists, String selfCode, List<FieldError> errors) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            errors.add(new FieldError("attrs.pairs", "색 코드 배열이어야 해요."));
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>();
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            if (!(v instanceof String s) || !colorExists.test(s)) {
                errors.add(new FieldError("attrs.pairs[" + i + "]", "있는 색 코드여야 해요."));
            } else if (s.equals(selfCode)) {
                errors.add(new FieldError("attrs.pairs[" + i + "]", "자기 자신은 넣을 수 없어요."));
            } else {
                out.add(s);
            }
        }
        return new ArrayList<>(out);
    }
}
