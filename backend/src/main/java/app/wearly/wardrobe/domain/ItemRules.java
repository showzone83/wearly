package app.wearly.wardrobe.domain;

import app.wearly.common.domain.FieldError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 옷장 아이템 입력의 불변식 (05 문서 6장 ItemInput): 이름 1~40자, 종류·세부 종류·색·패턴·계절·스타일·격식·보유 상태 필수, 격식 1~3, 브랜드 40자
 * 이하, 착용 수 0 이상, 계절·스타일 배열에 null 없음. 문구와 필드 이름은 웹 요청 검증(Bean Validation)과 같다. 코드 테이블 검증은
 * ItemCodeValidator가 한다.
 */
public final class ItemRules {

  public static final int NAME_MAX = 40;
  public static final int BRAND_MAX = 40;
  public static final int FORMALITY_MIN = 1;
  public static final int FORMALITY_MAX = 3;

  static final String REQUIRED = "필수 값이에요.";
  static final String NOT_ALLOWED = "허용되지 않는 값이에요.";

  private ItemRules() {}

  /**
   * @param prefix 필드 이름 앞에 붙일 경로 (bulk는 "items[3].")
   * @return 필드 이름 순으로 정렬한 오류 (없으면 빈 목록)
   */
  public static List<FieldError> validate(ItemInput in, String prefix) {
    List<FieldError> errors = new ArrayList<>();
    if (in.name() == null || in.name().isBlank()) {
      errors.add(new FieldError(prefix + "name", "이름을 입력해 주세요."));
    }
    if (in.name() != null && in.name().length() > NAME_MAX) {
      errors.add(new FieldError(prefix + "name", "이름은 40자까지 쓸 수 있어요."));
    }
    required(in.category(), prefix + "category", errors);
    required(in.subType(), prefix + "subType", errors);
    required(in.color(), prefix + "color", errors);
    required(in.pattern(), prefix + "pattern", errors);
    list(in.seasons(), prefix + "seasons", errors);
    list(in.styles(), prefix + "styles", errors);
    if (in.formality() == null) {
      errors.add(new FieldError(prefix + "formality", REQUIRED));
    } else if (in.formality() < FORMALITY_MIN || in.formality() > FORMALITY_MAX) {
      errors.add(new FieldError(prefix + "formality", "1~3이어야 해요."));
    }
    required(in.status(), prefix + "status", errors);
    if (in.brand() != null && in.brand().length() > BRAND_MAX) {
      errors.add(new FieldError(prefix + "brand", "브랜드는 40자까지 쓸 수 있어요."));
    }
    if (in.wornCount() != null && in.wornCount() < 0) {
      errors.add(new FieldError(prefix + "wornCount", "0 이상이어야 해요."));
    }
    errors.sort(Comparator.comparing(FieldError::field));
    return errors;
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
        errors.add(new FieldError(field + "[" + i + "]", NOT_ALLOWED));
      }
    }
  }
}
