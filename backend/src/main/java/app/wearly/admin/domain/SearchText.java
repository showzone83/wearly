package app.wearly.admin.domain;

/** 관리자 목록 검색어 정리 */
public final class SearchText {

    private SearchText() {
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** LIKE 특수문자를 이스케이프한 '%검색어%' (ESCAPE '\') */
    public static String containsPattern(String s) {
        return "%" + escapeLike(s) + "%";
    }

    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
