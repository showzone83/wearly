package app.wearly.wardrobe.domain;

import app.wearly.common.domain.FieldError;
import app.wearly.common.domain.FieldErrorCode;
import app.wearly.sharedkernel.code.CodeChecks;
import app.wearly.sharedkernel.code.CodeGroups;
import app.wearly.sharedkernel.code.CodeSnapshot;
import java.util.List;

/** 아이템 입력의 코드 검증: 그룹에 있는지, 새 값은 활성인지, subType의 parent가 category인지 */
public final class ItemCodeValidator {

  public static final FieldErrorCode SUBTYPE_MISMATCH = FieldErrorCode.SUBTYPE_MISMATCH;

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
    colors(codes, in, p == null ? null : p.colors(), prefix, errors);
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

  /** 색: 저장돼 있던 색(주·보조 어느 자리든)은 비활성이어도 둔다. 필드 이름은 colors[i] (예전 color 입력이면 첫 색은 color) */
  private static void colors(
      CodeSnapshot codes,
      ItemInput in,
      List<String> previous,
      String prefix,
      List<FieldError> errors) {
    List<String> colors = in.colors();
    if (colors == null) {
      return;
    }
    for (int i = 0; i < colors.size(); i++) {
      String c = colors.get(i);
      CodeChecks.one(
          codes,
          CodeGroups.COLOR,
          ItemRules.colorField(in, prefix, i),
          c,
          previous != null && previous.contains(c) ? c : null,
          errors);
    }
  }
}
