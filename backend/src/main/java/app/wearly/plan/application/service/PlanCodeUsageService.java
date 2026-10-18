package app.wearly.plan.application.service;

import app.wearly.plan.application.port.in.PlanCodeUsageUseCase;
import app.wearly.plan.application.port.in.dto.PlanCodeCountDto;
import app.wearly.plan.application.port.out.PlanQueryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 예정 코디의 코드 사용 수 (MyBatis). code 컨텍스트가 CodeUsageSource로 묻는다 (H1) */
@Service
public class PlanCodeUsageService implements PlanCodeUsageUseCase {

  private final PlanQueryPort queries;

  public PlanCodeUsageService(PlanQueryPort queries) {
    this.queries = queries;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanCodeCountDto> countByCode() {
    return queries.countByCode().stream()
        .map(c -> new PlanCodeCountDto(c.groupCode(), c.code(), c.count()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countPlansUsing(String group, String code) {
    return queries.countUsing(group, code);
  }
}
