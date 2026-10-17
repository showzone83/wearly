package app.wearly.admin.domain;

/** 관리자 목록 검색어 정리 */
public final class SearchText {

  private SearchText() {}

  public static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
