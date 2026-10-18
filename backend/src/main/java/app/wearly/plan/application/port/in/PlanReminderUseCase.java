package app.wearly.plan.application.port.in;

import app.wearly.plan.application.port.in.dto.PlanReminderDto;
import java.time.LocalDate;
import java.util.List;

/** 알림용 예정 조회 (notify 컨텍스트의 D-1 작업이 부른다). 회원을 가리지 않고 그 날짜의 예정을 모두 준다 */
public interface PlanReminderUseCase {

  /** 이 날짜의 모든 회원 예정 (회원 ID → 시간대 → ID 순) */
  List<PlanReminderDto> plansOn(LocalDate date);
}
