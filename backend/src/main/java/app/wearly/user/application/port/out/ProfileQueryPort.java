package app.wearly.user.application.port.out;

import app.wearly.user.application.port.out.dto.CodeCountDto;
import java.util.List;

/** 프로필 집계 (MyBatis, 읽기 전용): 코드 사용 수 */
public interface ProfileQueryPort {

  /** 모든 프로필이 쓰는 코드별 개수 (COLOR → colors, STYLE → moods, AGE_RANGE·BODY_TYPE·FIT → 나의 정보) */
  List<CodeCountDto> countByCode();

  /** 이 코드를 쓰는 프로필 수. 프로필이 쓰지 않는 그룹이면 0 */
  long countUsing(String group, String code);
}
