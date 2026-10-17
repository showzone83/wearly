package app.wearly.code.application.port.out;

import java.util.Map;

/** 코드 사용 수 집계 (MyBatis, 읽기 전용) */
public interface CodeUsagePort {

    /** 아이템이 쓰는 코드별 개수 (그룹 7개 전부). 키는 "GROUP:CODE" */
    Map<String, Long> itemUsage();

    long countItemsUsing(String group, String code);

    /** COLOR → 프로필 colors, STYLE → moods. 그 밖은 0 */
    long countProfilesUsing(String group, String code);
}
