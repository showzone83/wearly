package app.wearly.outfit.application.service;

import app.wearly.common.domain.FieldError;
import app.wearly.common.error.ApiException;
import app.wearly.outfit.application.port.in.dto.RecommendOutfitDto;
import app.wearly.outfit.domain.OutfitCodes;
import app.wearly.outfit.domain.OutfitRequest;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 코디 추천 요청 검증 (09 문서 3장). 틀린 곳을 모두 모아 400 VALIDATION_FAILED로 던진다. 아이템이 내 옷인지는 옷장을 읽은 뒤
 * OutfitPool.checkPins가 본다.
 */
final class OutfitRequestValidator {

  static final int MEMO_MAX = 200;
  static final int ID_LIST_MAX = 500;
  static final int RECENT_MAX = 31;
  static final int CODE_LIST_MAX = 30;

  private OutfitRequestValidator() {}

  static OutfitRequest validate(RecommendOutfitDto cmd, CodeSnapshot codes) {
    List<FieldError> errors = new ArrayList<>();
    if (cmd == null || cmd.context() == null) {
      throw ApiException.validation("context", "필수 값이에요.");
    }
    RecommendOutfitDto.Context c = cmd.context();
    final LocalDate date = date("context.date", c.date(), errors);
    final String occasion =
        oneOf("context.occasion", c.occasion(), OutfitCodes.OCCASIONS.keySet(), errors);
    final String mood = oneOf("context.mood", c.mood(), OutfitCodes.MOODS.keySet(), errors);
    final String formality =
        oneOf("context.formality", c.formality(), OutfitCodes.FORMALITIES.keySet(), errors);
    final OutfitRequest.Weather weather = weather(c.weather(), errors);
    final String anchor = blankToNull(c.anchorItemId());
    final Map<String, String> fixed = fixed(c.fixed(), errors);
    final List<String> avoidColors =
        codes("context.avoidColors", c.avoidColors(), codes, CodeGroups.COLOR, errors);
    final List<String> concepts =
        codes("context.concepts", c.concepts(), codes, CodeGroups.STYLE, errors);
    final String timeOfDay =
        c.timeOfDay() == null
            ? null
            : oneOf("context.timeOfDay", c.timeOfDay(), OutfitCodes.TIMES_OF_DAY.keySet(), errors);
    String memo = blankToNull(c.memo() == null ? null : c.memo().strip());
    if (memo != null && memo.codePointCount(0, memo.length()) > MEMO_MAX) {
      errors.add(new FieldError("context.memo", MEMO_MAX + "자 이하로 입력해 주세요."));
    }
    Set<String> exclude = ids("excludeItemIds", cmd.excludeItemIds(), errors);
    Set<String> liked = ids("likedItemIds", cmd.likedItemIds(), errors);
    Set<String> disliked = ids("dislikedItemIds", cmd.dislikedItemIds(), errors);
    List<OutfitRequest.RecentOutfit> recent = recent(cmd.recent(), errors);
    int count = cmd.count() == null ? OutfitRequest.DEFAULT_COUNT : cmd.count();
    if (count < 1 || count > OutfitRequest.MAX_COUNT) {
      errors.add(new FieldError("count", "1~" + OutfitRequest.MAX_COUNT + " 사이여야 해요."));
    }
    ApiException.throwIfAny(errors);
    return new OutfitRequest(
        date,
        occasion,
        mood,
        formality,
        weather,
        anchor,
        fixed,
        avoidColors,
        concepts,
        timeOfDay,
        memo,
        exclude,
        recent,
        liked,
        disliked,
        count);
  }

  private static LocalDate date(String field, String raw, List<FieldError> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(new FieldError(field, "필수 값이에요."));
      return null;
    }
    try {
      return LocalDate.parse(raw.strip());
    } catch (DateTimeParseException e) {
      errors.add(new FieldError(field, "YYYY-MM-DD 날짜여야 해요."));
      return null;
    }
  }

  private static String oneOf(
      String field, String raw, Collection<String> allowed, List<FieldError> errors) {
    if (raw == null || !allowed.contains(raw.strip())) {
      errors.add(new FieldError(field, String.join(", ", allowed) + " 중 하나여야 해요."));
      return null;
    }
    return raw.strip();
  }

  private static OutfitRequest.Weather weather(
      RecommendOutfitDto.Weather w, List<FieldError> errors) {
    if (w == null) {
      return null;
    }
    int before = errors.size();
    double temp = temperature("context.weather.temp", w.temp(), errors);
    double min = temperature("context.weather.min", w.min(), errors);
    double max = temperature("context.weather.max", w.max(), errors);
    if (w.rainProb() == null || w.rainProb().isNaN() || w.rainProb() < 0 || w.rainProb() > 100) {
      errors.add(new FieldError("context.weather.rainProb", "0~100 숫자여야 해요."));
    }
    String cond =
        oneOf("context.weather.cond", w.cond(), OutfitCodes.WEATHER_CONDITIONS.keySet(), errors);
    if (errors.size() > before) {
      return null;
    }
    return new OutfitRequest.Weather(temp, min, max, (int) Math.round(w.rainProb()), cond);
  }

  private static double temperature(String field, Double value, List<FieldError> errors) {
    if (value == null || value.isNaN() || value < -60 || value > 60) {
      errors.add(new FieldError(field, "-60~60 숫자여야 해요."));
      return 0;
    }
    return value;
  }

  /** 칸 고정: 6칸 이름만, 값은 아이템 id. 빈 값은 고정하지 않은 것으로 본다. 칸 순서로 정리한다 */
  private static Map<String, String> fixed(Map<String, String> raw, List<FieldError> errors) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    raw.forEach(
        (slot, id) -> {
          if (!OutfitCodes.SLOTS.contains(slot)) {
            errors.add(
                new FieldError(
                    "context.fixed." + slot,
                    String.join(", ", OutfitCodes.SLOTS) + " 칸만 고정할 수 있어요."));
          }
        });
    for (String slot : OutfitCodes.SLOTS) {
      String id = blankToNull(raw.get(slot));
      if (id != null) {
        out.put(slot, id);
      }
    }
    return out;
  }

  /** COLOR·STYLE 코드 목록: 코드 테이블에 있는 값만 (비활성 코드도 받는다), 중복 제거 */
  private static List<String> codes(
      String field, List<String> raw, CodeSnapshot codes, String group, List<FieldError> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw.size() > CODE_LIST_MAX) {
      errors.add(new FieldError(field, CODE_LIST_MAX + "개까지 보낼 수 있어요."));
      return List.of();
    }
    Set<String> out = new LinkedHashSet<>();
    for (String v : raw) {
      if (v == null || !codes.known(group, v)) {
        errors.add(new FieldError(field, "모르는 코드예요: " + v));
      } else {
        out.add(v);
      }
    }
    return List.copyOf(out);
  }

  /** 아이템 id 목록: 내 옷이 아닌 id는 버린다 (기기와 서버의 차이), 개수만 막는다 */
  private static Set<String> ids(String field, List<String> raw, List<FieldError> errors) {
    if (raw == null) {
      return Set.of();
    }
    if (raw.size() > ID_LIST_MAX) {
      errors.add(new FieldError(field, ID_LIST_MAX + "개까지 보낼 수 있어요."));
      return Set.of();
    }
    Set<String> out = new LinkedHashSet<>();
    raw.stream().map(OutfitRequestValidator::blankToNull).filter(v -> v != null).forEach(out::add);
    return out;
  }

  private static List<OutfitRequest.RecentOutfit> recent(
      List<RecommendOutfitDto.Recent> raw, List<FieldError> errors) {
    if (raw == null) {
      return List.of();
    }
    if (raw.size() > RECENT_MAX) {
      errors.add(new FieldError("recent", RECENT_MAX + "개까지 보낼 수 있어요."));
      return List.of();
    }
    List<OutfitRequest.RecentOutfit> out = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      RecommendOutfitDto.Recent r = raw.get(i);
      String field = "recent[" + i + "]";
      if (r == null) {
        errors.add(new FieldError(field, "필수 값이에요."));
        continue;
      }
      LocalDate date = date(field + ".date", r.date(), errors);
      Map<String, String> items = new LinkedHashMap<>();
      if (r.items() != null) {
        for (String slot : OutfitCodes.SLOTS) {
          String id = blankToNull(r.items().get(slot));
          if (id != null) {
            items.put(slot, id);
          }
        }
      }
      if (date != null) {
        out.add(new OutfitRequest.RecentOutfit(date, items));
      }
    }
    out.sort(Comparator.comparing(OutfitRequest.RecentOutfit::date).reversed());
    return out;
  }

  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.strip();
  }
}
