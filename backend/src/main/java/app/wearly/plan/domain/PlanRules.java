package app.wearly.plan.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 예정 코디 입력 규칙. 오류는 필드 오류 코드로 모아 돌려준다 (bulk는 prefix "items[3].").
 *
 * <ul>
 *   <li>date: 필수, YYYY-MM-DD, 오늘(한국 날짜) 1년 전 ~ 1년 뒤. PATCH에서 날짜를 바꾸지 않았으면 범위는 보지 않는다
 *   <li>timeOfDay: AM·PM·EVENING. occasion: 앱 Occasion 10개 (코드 테이블 없음, 고정)
 *   <li>title 1~50자, memo 500자, concepts는 STYLE 코드 10개까지(중복 금지, 비활성 코드는 저장된 값만)
 *   <li>place: label 1~50자, lat -90~90, lon -180~180, regionCode는 REGION 코드
 *   <li>weather: 필수. temp·min·max -60~60, rainProb 0~100, cond SUNNY·CLOUDY·RAIN. weatherSource는
 *       FORECAST·CLIMATE·MANUAL, weatherCheckedAt은 ISO-8601 시각
 *   <li>outfit: PlanOutfits
 *   <li>clientId: 1~64자 영문·숫자·._:- (bulk는 필수)
 * </ul>
 */
public final class PlanRules {

  public static final List<String> TIMES_OF_DAY = List.of("AM", "PM", "EVENING");
  public static final List<String> OCCASIONS =
      List.of(
          "COMMUTE",
          "MEETING",
          "FIELD",
          "DINNER",
          "DAILY",
          "INTERVIEW",
          "WEDDING",
          "DATE",
          "TRAVEL",
          "GATHERING");
  public static final List<String> CONDITIONS = List.of("SUNNY", "CLOUDY", "RAIN");
  public static final List<String> WEATHER_SOURCES = List.of("FORECAST", "CLIMATE", "MANUAL");

  public static final int TITLE_MAX = 50;
  public static final int MEMO_MAX = 500;
  public static final int LABEL_MAX = 50;
  public static final int CONCEPTS_MAX = 10;
  public static final int CLIENT_ID_MAX = 64;
  public static final int TEMP_MIN = -60;
  public static final int TEMP_MAX = 60;

  /** 회원당 저장할 수 있는 예정 수 */
  public static final int MAX_PLANS_PER_USER = 500;

  /** bulk 한 번에 올릴 수 있는 수 */
  public static final int BULK_MAX = 100;

  /** 날짜 범위: 오늘 기준 앞뒤 1년 */
  public static final int DATE_RANGE_YEARS = 1;

  private static final Pattern CLIENT_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

  private PlanRules() {}

  /**
   * @param previous PATCH·같은 clientId 갱신 전 저장된 값 (새로 만들면 null)
   * @param today 한국 날짜
   * @param prefix 필드 이름 앞에 붙일 경로 (bulk는 "items[3].")
   * @param clientIdRequired bulk면 true
   */
  public static List<FieldError> validate(
      PlanDraft d,
      PlanDraft previous,
      LocalDate today,
      CodeSnapshot codes,
      String prefix,
      boolean clientIdRequired) {
    List<FieldError> errors = new ArrayList<>();
    checkClientId(d.clientId(), prefix, clientIdRequired, errors);
    date(d.date(), previous == null ? null : previous.date(), today, prefix, errors);
    oneOf(d.timeOfDay(), TIMES_OF_DAY, prefix + "timeOfDay", errors);
    String title = strip(d.title());
    if (title == null || title.isEmpty()) {
      errors.add(FieldError.required(prefix + "title"));
    } else if (title.length() > TITLE_MAX) {
      errors.add(FieldError.tooLong(prefix + "title", TITLE_MAX));
    }
    oneOf(d.occasion(), OCCASIONS, prefix + "occasion", errors);
    concepts(d.concepts(), previous == null ? null : previous.concepts(), codes, prefix, errors);
    String memo = strip(d.memo());
    if (memo != null && memo.length() > MEMO_MAX) {
      errors.add(FieldError.tooLong(prefix + "memo", MEMO_MAX));
    }
    place(d.place(), previous == null ? null : previous.place(), codes, prefix, errors);
    weather(d.weather(), prefix, errors);
    if (d.weatherSource() != null) {
      oneOf(d.weatherSource(), WEATHER_SOURCES, prefix + "weatherSource", errors);
    }
    if (d.weatherCheckedAt() != null && instant(d.weatherCheckedAt()) == null) {
      errors.add(FieldError.invalidFormat(prefix + "weatherCheckedAt", "ISO-8601"));
    }
    PlanOutfits.validate(d.outfit(), prefix, errors);
    return errors;
  }

  /** 검증을 통과한 입력 → 저장 값 (앞뒤 공백 정리, 빈 메모는 null) */
  public static PlanInput toInput(PlanDraft d) {
    PlanDraft.Place p = d.place();
    PlanDraft.Weather w = d.weather();
    String memo = strip(d.memo());
    return new PlanInput(
        LocalDate.parse(d.date().strip()),
        d.timeOfDay(),
        strip(d.title()),
        d.occasion(),
        d.concepts() == null ? List.of() : d.concepts(),
        memo == null || memo.isEmpty() ? null : memo,
        p == null
            ? null
            : new PlanPlace(blankToNull(p.regionCode()), strip(p.label()), p.lat(), p.lon()),
        new PlanWeather(
            w.temp(),
            w.min(),
            w.max(),
            w.rainProb(),
            w.cond(),
            Boolean.TRUE.equals(w.manual()),
            d.weatherSource(),
            d.weatherCheckedAt() == null ? null : instant(d.weatherCheckedAt())),
        Boolean.TRUE.equals(d.forecastReady()),
        d.outfit() == null ? null : new LinkedHashMap<>(d.outfit()));
  }

  /** clientId 앞뒤 공백을 정리한다 (없거나 비면 null) */
  public static String clientId(String raw) {
    return blankToNull(raw);
  }

  /** 날짜 범위의 처음·끝 (오늘 기준 앞뒤 1년) */
  public static LocalDate minDate(LocalDate today) {
    return today.minusYears(DATE_RANGE_YEARS);
  }

  public static LocalDate maxDate(LocalDate today) {
    return today.plusYears(DATE_RANGE_YEARS);
  }

  private static void checkClientId(
      String raw, String prefix, boolean required, List<FieldError> errors) {
    String field = prefix + "clientId";
    String value = blankToNull(raw);
    if (value == null) {
      if (required) {
        errors.add(FieldError.required(field));
      }
      return;
    }
    if (value.length() > CLIENT_ID_MAX) {
      errors.add(FieldError.tooLong(field, CLIENT_ID_MAX));
    } else if (!CLIENT_ID.matcher(value).matches()) {
      errors.add(FieldError.invalidFormat(field));
    }
  }

  private static void date(
      String raw, String previous, LocalDate today, String prefix, List<FieldError> errors) {
    String field = prefix + "date";
    if (raw == null || raw.isBlank()) {
      errors.add(FieldError.required(field));
      return;
    }
    LocalDate date;
    try {
      date = LocalDate.parse(raw.strip());
    } catch (DateTimeParseException e) {
      errors.add(FieldError.invalidFormat(field, "YYYY-MM-DD"));
      return;
    }
    if (previous != null && date.toString().equals(previous)) {
      // 바꾸지 않은 날짜는 범위를 보지 않는다 (1년 넘게 지난 예정의 메모만 고칠 때)
      return;
    }
    LocalDate min = minDate(today);
    LocalDate max = maxDate(today);
    if (date.isBefore(min) || date.isAfter(max)) {
      // 날짜 범위: min·max는 YYYY-MM-DD 문자열 (19 문서 /weather/day의 date와 같은 방식)
      errors.add(
          new FieldError(
              field,
              FieldErrorCode.OUT_OF_RANGE,
              Map.of("min", min.toString(), "max", max.toString())));
    }
  }

  private static void concepts(
      List<String> values,
      List<String> previous,
      CodeSnapshot codes,
      String prefix,
      List<FieldError> errors) {
    if (values == null) {
      return;
    }
    String field = prefix + "concepts";
    if (values.size() > CONCEPTS_MAX) {
      errors.add(FieldError.tooMany(field, CONCEPTS_MAX));
      return;
    }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < values.size(); i++) {
      String v = values.get(i);
      if (v == null) {
        errors.add(FieldError.required(field + "[" + i + "]"));
      } else if (!seen.add(v)) {
        errors.add(new FieldError(field + "[" + i + "]", FieldErrorCode.DUPLICATE));
      }
    }
    CodeChecks.many(codes, CodeGroups.STYLE, field, values, previous, errors);
  }

  private static void place(
      PlanDraft.Place p,
      PlanDraft.Place previous,
      CodeSnapshot codes,
      String prefix,
      List<FieldError> errors) {
    if (p == null) {
      return;
    }
    String field = prefix + "place.";
    String label = strip(p.label());
    if (label == null || label.isEmpty()) {
      errors.add(FieldError.required(field + "label"));
    } else if (label.length() > LABEL_MAX) {
      errors.add(FieldError.tooLong(field + "label", LABEL_MAX));
    }
    range(p.lat(), -90, 90, field + "lat", errors);
    range(p.lon(), -180, 180, field + "lon", errors);
    String region = blankToNull(p.regionCode());
    if (region != null) {
      CodeChecks.one(
          codes,
          CodeGroups.REGION,
          field + "regionCode",
          region,
          previous == null ? null : blankToNull(previous.regionCode()),
          errors);
    }
  }

  private static void weather(PlanDraft.Weather w, String prefix, List<FieldError> errors) {
    String field = prefix + "weather";
    if (w == null) {
      errors.add(FieldError.required(field));
      return;
    }
    range(w.temp(), TEMP_MIN, TEMP_MAX, field + ".temp", errors);
    range(w.min(), TEMP_MIN, TEMP_MAX, field + ".min", errors);
    range(w.max(), TEMP_MIN, TEMP_MAX, field + ".max", errors);
    range(w.rainProb(), 0, 100, field + ".rainProb", errors);
    oneOf(w.cond(), CONDITIONS, field + ".cond", errors);
  }

  private static void range(Double v, int min, int max, String field, List<FieldError> errors) {
    if (v == null) {
      errors.add(FieldError.required(field));
    } else if (v.isNaN() || v < min || v > max) {
      errors.add(FieldError.outOfRange(field, min, max));
    }
  }

  private static void oneOf(String v, List<String> allowed, String field, List<FieldError> errors) {
    if (v == null || v.isBlank()) {
      errors.add(FieldError.required(field));
    } else if (!allowed.contains(v)) {
      errors.add(FieldError.notAllowed(field, allowed));
    }
  }

  /** ISO-8601 시각 (Z 또는 +09:00 같은 오프셋). 틀리면 null */
  static Instant instant(String raw) {
    try {
      return OffsetDateTime.parse(raw.strip()).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String strip(String s) {
    return s == null ? null : s.strip();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
