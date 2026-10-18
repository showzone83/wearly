package app.wearly.sharedkernel.code;

/**
 * SEASON 그룹의 기본 코드 (06 문서 초기값). 기온으로 계절을 고르는 코디 추천이 쓴다 (L5). 코드 값은 관리자가 바꿀 수 없고(이름 변경 없음) 비활성만 할 수
 * 있다. 계절-기온 대응을 코드 attrs로 옮기면 /codes 응답 계약(06 문서)이 바뀌므로 고정값으로 둔다. 초봄·초여름·초가을은 이슈 #4(V20)에서 더했다.
 */
public final class Seasons {

  public static final String EARLY_SPRING = "EARLY_SPRING";
  public static final String SPRING = "SPRING";
  public static final String EARLY_SUMMER = "EARLY_SUMMER";
  public static final String SUMMER = "SUMMER";
  public static final String EARLY_FALL = "EARLY_FALL";
  public static final String FALL = "FALL";
  public static final String WINTER = "WINTER";

  private Seasons() {}
}
