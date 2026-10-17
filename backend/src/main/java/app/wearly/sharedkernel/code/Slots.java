package app.wearly.sharedkernel.code;

import java.util.List;

/**
 * 코디 칸 (앱 로직 고정값, 06 문서 5장). CATEGORY 코드의 attrs.slot 값이고(code가 검증), outfit이 칸을 채울 때 쓴다. 칸 순서도 이
 * 순서다.
 */
public final class Slots {

  public static final String TOP = "TOP";
  public static final String BOTTOM = "BOTTOM";
  public static final String DRESS = "DRESS";
  public static final String OUTER = "OUTER";
  public static final String SHOES = "SHOES";
  public static final String BAG = "BAG";

  public static final List<String> ALL = List.of(TOP, BOTTOM, DRESS, OUTER, SHOES, BAG);

  private Slots() {}
}
