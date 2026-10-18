package app.wearly.plan.application.port.out.dto;

/** 코드별 예정 수 (그룹, 코드, 개수). 매퍼가 바로 돌려준다 */
public record PlanCodeCountRowDto(String groupCode, String code, long count) {}
