package app.wearly.wardrobe.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.List;

/** 아이템 입력의 코드 검증: 그룹에 있는지, 새 값은 활성인지, subType의 parent가 category인지 */
public final class ItemCodeValidator {

  public static final String SUBTYPE_MISMATCH = "카테고리와 맞지 않는 세부 종류예요.";

  private ItemCodeValidator() {}

  /**
   * @param previous PATCH 전 저장된 값 (등록이면 null)
   * @param prefix 필드 이름 앞에 붙일 경로 (bulk는 "items[3].")
   */
  public static void validate(
      CodeSnapshot codes,
      ItemInput in,
      ItemInput previous,
      String prefix,
      List<FieldError> errors) {
    ItemInput p = previous;
    CodeChecks.one(
        codes,
        CodeGroups.CATEGORY,
        prefix + "category",
        in.category(),
        p == null ? null : p.category(),
        errors);
    int before = errors.size();
    CodeChecks.one(
        codes,
        CodeGroups.SUB_TYPE,
        prefix + "subType",
        in.subType(),
        p == null ? null : p.subType(),
        errors);
    boolean subTypeOk = errors.size() == before;
    if (subTypeOk
        && in.subType() != null
        && in.category() != null
        && codes.known(CodeGroups.CATEGORY, in.category())
        && !codes.belongsTo(in.subType(), in.category())) {
      errors.add(new FieldError(prefix + "subType", SUBTYPE_MISMATCH));
    }
    CodeChecks.one(
        codes,
        CodeGroups.COLOR,
        prefix + "color",
        in.color(),
        p == null ? null : p.color(),
        errors);
    CodeChecks.one(
        codes,
        CodeGroups.PATTERN,
        prefix + "pattern",
        in.pattern(),
        p == null ? null : p.pattern(),
        errors);
    CodeChecks.one(
        codes,
        CodeGroups.ITEM_STATUS,
        prefix + "status",
        in.status(),
        p == null ? null : p.status(),
        errors);
    CodeChecks.many(
        codes,
        CodeGroups.SEASON,
        prefix + "seasons",
        in.seasons(),
        p == null ? null : p.seasons(),
        errors);
    CodeChecks.many(
        codes,
        CodeGroups.STYLE,
        prefix + "styles",
        in.styles(),
        p == null ? null : p.styles(),
        errors);
  }
}
