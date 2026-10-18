package app.wearly.plan.adapter.in.code;

import app.wearly.code.application.port.in.CodeUsageSource;
import app.wearly.code.application.port.in.dto.CodeUsageCountDto;
import app.wearly.plan.application.port.in.PlanCodeUsageUseCase;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * code 컨텍스트가 정의한 코드 사용처(CodeUsageSource)를 예정 코디로 구현한다 (H1). 예정이 쓰는 STYLE·REGION 코드는 삭제할 수 없고(409
 * CODE_IN_USE, usages kind PLAN) 비활성화만 할 수 있다
 */
@Component
class PlanCodeUsageSource implements CodeUsageSource {

  private final PlanCodeUsageUseCase usage;

  PlanCodeUsageSource(PlanCodeUsageUseCase usage) {
    this.usage = usage;
  }

  @Override
  public Kind kind() {
    return Kind.PLAN;
  }

  @Override
  public List<CodeUsageCountDto> countByCode() {
    return usage.countByCode().stream()
        .map(c -> new CodeUsageCountDto(c.group(), c.code(), c.count()))
        .toList();
  }

  @Override
  public long countUsing(String group, String code) {
    return usage.countPlansUsing(group, code);
  }
}
