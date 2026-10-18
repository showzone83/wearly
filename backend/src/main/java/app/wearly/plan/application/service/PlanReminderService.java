package app.wearly.plan.application.service;

import app.wearly.plan.application.port.in.PlanReminderUseCase;
import app.wearly.plan.application.port.in.dto.PlanReminderDto;
import app.wearly.plan.application.port.out.PlanQueryPort;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림용 예정 조회 (MyBatis) */
@Service
public class PlanReminderService implements PlanReminderUseCase {

  private final PlanQueryPort queries;

  public PlanReminderService(PlanQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanReminderDto> plansOn(LocalDate date) {
    return queries.findOnDate(date).stream().map(PlanReminderDto::from).toList();
  }
}
