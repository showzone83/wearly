package app.wearly.common.mybatis;

/**
 * MyBatis XML에서 쓰는 LIKE 패턴 도우미 (L3). 포트는 원래 검색어를 넘기고, 쿼리 어댑터(XML)가 {@code <bind name="p"
 * value="@app.wearly.common.mybatis.LikePatterns@contains(query)"/>}로 패턴을 만든 뒤 {@code ILIKE #{p}
 * ESCAPE '\'}로 쓴다.
 */
public final class LikePatterns {

  private LikePatterns() {}

  /** LIKE 특수문자(\ % _)를 이스케이프한 '%검색어%'. 검색어가 null이면 null */
  public static String contains(String text) {
    return text == null ? null : "%" + escape(text) + "%";
  }

  static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
