package app.wearly.plan.application.port.in;

import app.wearly.plan.application.port.in.dto.PlanCodeCountDto;
import java.util.List;

/**
 * 예정 코디의 코드 사용 수 (모든 회원): concepts → STYLE, place.regionCode → REGION. code 컨텍스트의 삭제 가드가 입력
 * 어댑터(adapter.in.code)를 거쳐 부른다 (H1)
 */
public interface PlanCodeUsageUseCase {

  List<PlanCodeCountDto> countByCode();

  long countPlansUsing(String group, String code);
}
