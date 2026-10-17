package app.wearly.wardrobe;

import app.wearly.code.CodeGroups;
import app.wearly.code.CodeSnapshot;
import app.wearly.common.error.ErrorResponse.FieldError;
import java.util.List;
import java.util.Objects;

/**
 * 아이템 입력의 코드 값 검증 (doc/06_코드_관리.md 4장).
 * - 모든 값은 그룹에 있는 코드여야 하고, subType의 상위는 category여야 한다.
 * - 새로 고르는 값은 활성 코드만 받는다. PATCH에서 바꾸지 않은 값(previous와 같은 값)은 비활성이어도 그대로 둔다.
 */
final class ItemCodeValidator {

    static final String UNKNOWN = "허용되지 않는 값이에요.";
    static final String INACTIVE = "더 이상 고를 수 없는 값이에요.";
    static final String MISMATCH = "카테고리와 맞지 않는 세부 종류예요.";

    private ItemCodeValidator() {
    }

    /**
     * @param previous PATCH 전 저장값 (등록이면 null)
     * @param prefix   필드 이름 앞에 붙일 경로 (bulk의 "items[3]." 등)
     */
    static void validate(CodeSnapshot codes, ItemInput in, ItemInput previous, String prefix, List<FieldError> errors) {
        one(codes, CodeGroups.CATEGORY, in.category(), previous == null ? null : previous.category(), prefix + "category",
                errors);
        boolean subTypeOk = one(codes, CodeGroups.SUB_TYPE, in.subType(), previous == null ? null : previous.subType(),
                prefix + "subType", errors);
        if (subTypeOk && in.category() != null && !codes.belongsTo(in.subType(), in.category())) {
            errors.add(new FieldError(prefix + "subType", MISMATCH));
        }
        one(codes, CodeGroups.COLOR, in.color(), previous == null ? null : previous.color(), prefix + "color", errors);
        one(codes, CodeGroups.PATTERN, in.pattern(), previous == null ? null : previous.pattern(), prefix + "pattern",
                errors);
        one(codes, CodeGroups.ITEM_STATUS, in.status(), previous == null ? null : previous.status(), prefix + "status",
                errors);
        many(codes, CodeGroups.SEASON, in.seasons(), previous == null ? null : previous.seasons(), prefix + "seasons",
                errors);
        many(codes, CodeGroups.STYLE, in.styles(), previous == null ? null : previous.styles(), prefix + "styles", errors);
    }

    /** @return 코드가 있으면 true (활성 여부와 상관없이) */
    private static boolean one(CodeSnapshot codes, String group, String value, String previous, String field,
            List<FieldError> errors) {
        if (value == null) {
            return false; // 빈 값은 Bean Validation이 잡는다
        }
        if (!codes.exists(group, value)) {
            errors.add(new FieldError(field, UNKNOWN));
            return false;
        }
        if (!Objects.equals(value, previous) && !codes.selectable(group, value)) {
            errors.add(new FieldError(field, INACTIVE));
        }
        return true;
    }

    private static void many(CodeSnapshot codes, String group, List<String> values, List<String> previous, String field,
            List<FieldError> errors) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                continue;
            }
            if (!codes.exists(group, value)) {
                errors.add(new FieldError(field + "[" + i + "]", UNKNOWN));
            } else if ((previous == null || !previous.contains(value)) && !codes.selectable(group, value)) {
                errors.add(new FieldError(field + "[" + i + "]", INACTIVE));
            }
        }
    }
}
