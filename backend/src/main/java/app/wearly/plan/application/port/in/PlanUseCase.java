package app.wearly.plan.application.port.in;

import app.wearly.plan.application.port.in.dto.PatchPlanDto;
import app.wearly.plan.application.port.in.dto.PlanBulkResultDto;
import app.wearly.plan.application.port.in.dto.PlanDto;
import app.wearly.plan.application.port.in.dto.PlanInputDto;
import app.wearly.plan.application.port.in.dto.PlanSaveResultDto;
import app.wearly.plan.application.port.in.dto.PlanSearchDto;
import java.util.List;

/** 예정 코디 서버 저장 (19 문서 "다음 단계"). 회원마다 최대 500개. 남의 예정은 없는 것으로 본다 (404) */
public interface PlanUseCase {

  /** 날짜(오래된 순) → 시간대(AM·PM·EVENING) → ID 순 */
  List<PlanDto> list(PlanSearchDto search);

  /** clientId가 있고 같은 clientId의 예정이 있으면 새로 만들지 않고 그 예정을 보낸 값으로 바꾼다 */
  PlanSaveResultDto create(long userId, PlanInputDto input);

  /**
   * 기기에 있던 예정을 한 번에 올린다 (최대 100개, clientId 필수·요청 안에서 중복 금지). 같은 clientId가 이미 있으면 고치고 없으면 만든다. 하나라도
   * 틀리면 전부 저장하지 않는다
   */
  PlanBulkResultDto bulk(long userId, List<PlanInputDto> inputs);

  /** 저장된 값 위에 보낸 필드만 덮어쓴 뒤 등록과 같은 검증을 한다. 없는·남의 예정이면 404가 먼저 */
  PlanDto patch(long userId, long planId, PatchPlanDto patch);

  void delete(long userId, long planId);
}
