package app.wearly.wardrobe.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 옷장 아이템 입력의 불변식 (05 문서 6장 ItemInput): 이름 1~40자, 종류·세부 종류·색·패턴·계절·스타일·격식·보유 상태 필수, 격식 1~3, 브랜드 40자
 * 이하, 착용 수 0 이상, 계절·스타일 배열에 null 없음. 색은 1~3개(첫 번째가 주 색상, 중복 없음), 구입일은 1990-01-01 ~ 오늘(서울) (이슈 #4).
 * 필드 이름·오류 코드는 웹 요청 검증(Bean Validation)과 같다. 코드 테이블 검증은 ItemCodeValidator가 한다.
 */
public final class ItemRules {

  public static final int NAME_MAX = 40;
  public static final int BRAND_MAX = 40;
  public static final int FORMALITY_MIN = 1;
  public static final int FORMALITY_MAX = 3;

  /** 색은 주 색상 포함 3개까지 */
  public static final int COLORS_MAX = 3;

  /** 구입일은 이 날부터 */
  public static final LocalDate PURCHASED_MIN = LocalDate.of(1990, 1, 1);

  static final FieldErrorCode REQUIRED = FieldErrorCode.REQUIRED;

  private ItemRules() {}

  /**
   * @param prefix 필드 이름 앞에 붙일 경로 (bulk는 "items[3].")
   * @param today 오늘 (서울). 구입일 상한
   * @return 필드 이름 순으로 정렬한 오류 (없으면 빈 목록)
   */
  public static List<FieldError> validate(ItemInput in, String prefix, LocalDate today) {
    List<FieldError> errors = new ArrayList<>();
    if (in.name() == null || in.name().isBlank()) {
      errors.add(new FieldError(prefix + "name", REQUIRED));
    }
    if (in.name() != null && in.name().length() > NAME_MAX) {
      errors.add(FieldError.tooLong(prefix + "name", NAME_MAX));
    }
    required(in.category(), prefix + "category", errors);
    required(in.subType(), prefix + "subType", errors);
    colors(in, prefix, errors);
    required(in.pattern(), prefix + "pattern", errors);
    list(in.seasons(), prefix + "seasons", errors);
    list(in.styles(), prefix + "styles", errors);
    if (in.formality() == null) {
      errors.add(new FieldError(prefix + "formality", REQUIRED));
    } else if (in.formality() < FORMALITY_MIN || in.formality() > FORMALITY_MAX) {
      errors.add(FieldError.outOfRange(prefix + "formality", FORMALITY_MIN, FORMALITY_MAX));
    }
    required(in.status(), prefix + "status", errors);
    if (in.brand() != null && in.brand().length() > BRAND_MAX) {
      errors.add(FieldError.tooLong(prefix + "brand", BRAND_MAX));
    }
    if (in.wornCount() != null && in.wornCount() < 0) {
      errors.add(FieldError.outOfRange(prefix + "wornCount", 0, null));
    }
    LocalDate bought = in.purchasedAt();
    if (bought != null && (bought.isBefore(PURCHASED_MIN) || bought.isAfter(today))) {
      // 날짜 범위: min·max는 YYYY-MM-DD 문자열 (예정 코디 date와 같은 방식)
      errors.add(
          new FieldError(
              prefix + "purchasedAt",
              FieldErrorCode.OUT_OF_RANGE,
              dateRange(PURCHASED_MIN, today)));
    }
    errors.sort(Comparator.comparing(FieldError::field));
    return errors;
  }

  /** 날짜 범위 params: min·max 순서를 고정한다 (응답 JSON이 요청마다 같게) */
  private static Map<String, Object> dateRange(LocalDate min, LocalDate max) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("min", min.toString());
    p.put("max", max.toString());
    return p;
  }

  /** 색 오류를 알릴 필드 이름: 예전 color로 들어온 값이면 color, 아니면 colors[i] */
  public static String colorField(ItemInput in, String prefix, int index) {
    return in.legacyColor() && index == 0 ? prefix + "color" : prefix + "colors[" + index + "]";
  }

  /** 색 목록: 1~3개, null 없음, 중복 없음 */
  private static void colors(ItemInput in, String prefix, List<FieldError> errors) {
    List<String> colors = in.colors();
    if (colors == null || colors.isEmpty()) {
      errors.add(new FieldError(prefix + (in.legacyColor() ? "color" : "colors"), REQUIRED));
      return;
    }
    if (colors.size() > COLORS_MAX) {
      errors.add(FieldError.tooMany(prefix + "colors", COLORS_MAX));
    }
    for (int i = 0; i < colors.size(); i++) {
      String c = colors.get(i);
      if (c == null) {
        errors.add(new FieldError(colorField(in, prefix, i), REQUIRED));
      } else if (colors.subList(0, i).contains(c)) {
        errors.add(new FieldError(colorField(in, prefix, i), FieldErrorCode.DUPLICATE));
      }
    }
  }

  private static void required(Object value, String field, List<FieldError> errors) {
    if (value == null) {
      errors.add(new FieldError(field, REQUIRED));
    }
  }

  private static void list(List<String> values, String field, List<FieldError> errors) {
    if (values == null) {
      errors.add(new FieldError(field, REQUIRED));
      return;
    }
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i) == null) {
        errors.add(new FieldError(field + "[" + i + "]", REQUIRED));
      }
    }
  }
}
