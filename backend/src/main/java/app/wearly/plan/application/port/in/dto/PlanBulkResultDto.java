package app.wearly.plan.application.port.in.dto;

import java.util.List;

/**
 * 예정 한꺼번에 올리기 결과 (요청 순서대로).
 *
 * @param created 새로 만든 수
 * @param updated 같은 clientId라 고친 수
 */
public record PlanBulkResultDto(List<PlanDto> items, int created, int updated) {}
