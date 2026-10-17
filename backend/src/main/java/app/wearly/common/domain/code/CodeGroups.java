package app.wearly.common.domain.code;

import java.util.List;

/** 코드 그룹 이름 (06 문서 2장). 그룹 자체는 tb_code_group에 있고 관리자 API로 늘리지 않는다. */
public final class CodeGroups {

    public static final String CATEGORY = "CATEGORY";
    public static final String SUB_TYPE = "SUB_TYPE";
    public static final String COLOR = "COLOR";
    public static final String PATTERN = "PATTERN";
    public static final String SEASON = "SEASON";
    public static final String STYLE = "STYLE";
    public static final String ITEM_STATUS = "ITEM_STATUS";

    public static final List<String> ALL = List.of(CATEGORY, SUB_TYPE, COLOR, PATTERN, SEASON, STYLE, ITEM_STATUS);

    /** 코디 칸 (앱 로직 고정값, 06 문서 5장). CATEGORY attrs.slot 값 */
    public static final List<String> SLOTS = List.of("TOP", "BOTTOM", "DRESS", "OUTER", "SHOES", "BAG");

    private CodeGroups() {
    }
}
