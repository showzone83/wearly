package app.wearly.sharedkernel.code;

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

  /** 옷을 사용하지 않는 옷으로 옮긴 이유 (13 문서 2장). attrs 없음 */
  public static final String RETIRE_REASON = "RETIRE_REASON";

  /** 날씨를 받아 두는 지역 (17 문서 2장). attrs.lat·lon */
  public static final String REGION = "REGION";

  /** 프로필 나의 정보: 연령대 (GitHub 이슈 #5). attrs 없음 */
  public static final String AGE_RANGE = "AGE_RANGE";

  /** 프로필 나의 정보: 체형 (골격 유형). attrs.description·hint */
  public static final String BODY_TYPE = "BODY_TYPE";

  /** 프로필 나의 정보: 핏 선호. attrs 없음 */
  public static final String FIT = "FIT";

  public static final List<String> ALL =
      List.of(
          CATEGORY,
          SUB_TYPE,
          COLOR,
          PATTERN,
          SEASON,
          STYLE,
          ITEM_STATUS,
          RETIRE_REASON,
          REGION,
          AGE_RANGE,
          BODY_TYPE,
          FIT);

  private CodeGroups() {}
}
