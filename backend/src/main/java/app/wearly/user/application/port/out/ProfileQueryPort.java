package app.wearly.user.application.port.out;

import app.wearly.user.application.port.out.dto.CodeCountDto;
import java.util.Collection;
import java.util.List;

/** 프로필 집계 (MyBatis, 읽기 전용): 코드 사용 수 */
public interface ProfileQueryPort {

  /** 모든 프로필이 쓰는 코드별 개수 (COLOR → colors, STYLE → moods, AGE_RANGE·BODY_TYPE·FIT → 나의 정보) */
  List<CodeCountDto> countByCode();

  /** 이 코드를 쓰는 프로필 수. 프로필이 쓰지 않는 그룹이면 0 */
  long countUsing(String group, String code);

  /** 이 회원들 중 예정 전날 알림(notify.dMinus1)을 켠 활성 회원 ID. 프로필이 없으면 기본값(켬)으로 본다 */
  List<Long> findDayBeforeEnabled(Collection<Long> userIds);
}
