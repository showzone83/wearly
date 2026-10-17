package app.wearly.common.web;

/** JSON에서 ID는 문자열이다 (05 문서 4장). 경로의 ID 해석은 웹 어댑터의 PathIds가 한다. */
public final class Ids {

  private Ids() {}

  public static String format(Long id) {
    return id == null ? null : Long.toString(id);
  }
}
