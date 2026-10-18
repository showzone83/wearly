package app.wearly.plan.application.port.in.dto;

/**
 * 예정 등록 결과.
 *
 * @param created 새로 만들었으면 true, 같은 clientId의 예정을 고쳤으면 false
 */
public record PlanSaveResultDto(PlanDto plan, boolean created) {}
